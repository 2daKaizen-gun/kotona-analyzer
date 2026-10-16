package com.kaizen.kotona.analyzer.service;

import com.kaizen.kotona.analyzer.dto.EvaluationDTO;
import com.kaizen.kotona.analyzer.dto.MetricsDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.dto.RelationshipType;
import com.kaizen.kotona.analyzer.dto.RiskAnalysisDTO;
import com.kaizen.kotona.analyzer.dto.ScoreAdjustmentDTO;
import com.kaizen.kotona.analyzer.dto.SmartReplyDTO;
import com.kaizen.kotona.analyzer.dto.SuggestionDTO;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;
import com.kaizen.kotona.analyzer.utils.JapaneseOutputSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisValidator {

    /** 사전을 기본형으로 맞추기 위해 쓴다. 활용형에 걸려 넘어지지 않게 하는 유일한 수단이다. */
    private final JapaneseTokenService tokenService;

    //AI 점수와 형태소 분석 및 규칙 기반 데이터 교차 검증
    public NuanceResponseDTO validate(NuanceResponseDTO aiResponse, String cleanInput, RelationshipType relationshipType, boolean hasPoliteEnding) {
        Set<String> lemmas = tokenService.baseForms(cleanInput);
        // 규칙이 손댄 것은 전부 적어 둔다. 적지 않으면 사용자는 73점의 이유를 알 수 없다.
        List<ScoreAdjustmentDTO> adjustments = new ArrayList<>();
        // 기존 Metrics 가져오기. 배점 밖의 값은 먼저 배점 안으로 들인다 —
        // 스키마는 설명으로만 범위를 말할 뿐 강제하지 않아서, -20 이 오면 총점 -10 이 그대로 나갔다.
        int p = withinScale("politeness", aiResponse.metrics().politeness(), POLITENESS_MAX, adjustments);
        int i = withinScale("indirectness", aiResponse.metrics().indirectness(), INDIRECTNESS_MAX, adjustments);
        int e = withinScale("etiquette", aiResponse.metrics().etiquette(), ETIQUETTE_MAX, adjustments);

        // 1. Politeness 검증 (경어 사용 여부)
        if (p >= 30 && !hasPoliteEnding) {
            int before = p;
            p = Math.max(0, p - 10); // 10점 페널티
            adjustments.add(new ScoreAdjustmentDTO("politeness", String.valueOf(before), String.valueOf(p),
                    "정중체(です・ます 또는 〜ください)가 쓰이지 않아 감점했습니다."));
        }

        // 2. Etiquette 검증 (쿠션어 사용 여부)
        boolean hasCushion = lemmas.stream().anyMatch(EtiquetteConstants.CUSHION_LEMMAS::contains)
                || EtiquetteConstants.CUSHION_PHRASES.stream().anyMatch(cleanInput::contains);
        if (e >= 20 && !hasCushion) {
            int before = e;
            e = Math.max(0, e - 10); // 10점 페널티
            adjustments.add(new ScoreAdjustmentDTO("etiquette", String.valueOf(before), String.valueOf(e),
                    "쿠션어(お手数ですが・恐れ入りますが 등)가 쓰이지 않아 감점했습니다."));
        }

        // 3. Indirectness 검증 (완곡 어미 사용 여부)
        boolean hasIndirect = EtiquetteConstants.INDIRECT_ENDINGS.stream()
                .anyMatch(cleanInput::contains);
        if (i >= 20 && !hasIndirect) {
            int before = i;
            i = Math.max(0, i - 5); // 5점 페널티
            adjustments.add(new ScoreAdjustmentDTO("indirectness", String.valueOf(before), String.valueOf(i),
                    "완곡 어미(〜でしょうか 등)가 쓰이지 않아 감점했습니다."));
        }

        // 최종 총점 재계산 (합계가 100을 넘지 않도록 보정)
        int finalTotal = Math.min(100, p + i + e);

        // 리스크 가중치 산출 로직
        // 공식: Risk Score = Σ(Signal_i * W_i)
        double riskScore = 0.0;
        List<String> detectedRedFlags = new ArrayList<>();

        // 신호는 문맥을 거친 낱말로 센다 — 「ご検討ください」 의 検討 는 보류가 아니라 부탁이다.
        Set<String> refusalLemmas = tokenService.refusalLemmas(cleanInput);
        for (Map.Entry<String, EtiquetteConstants.SoftRejectionSignal> entry
                : EtiquetteConstants.SOFT_REJECTION_SIGNALS.entrySet()) {
            if (EtiquetteConstants.signalMatches(entry, refusalLemmas)) {
                riskScore += entry.getValue().weight();
                detectedRedFlags.add(entry.getValue().description());
            }
        }
        // 굳은 표현은 낱말로 환원되지 않으므로 구 그대로 찾는다.
        for (Map.Entry<String, EtiquetteConstants.SoftRejectionSignal> entry
                : EtiquetteConstants.SOFT_REJECTION_PHRASES.entrySet()) {
            if (cleanInput.contains(entry.getKey())) {
                riskScore += entry.getValue().weight();
                detectedRedFlags.add(entry.getValue().description());
            }
        }

        // 컨텍스트 가중치($W$) 적용. 배수는 enum 이 들고 있다 — 값이 늘어날 때
        // default 가 조용히 1.0 을 주는 일을 막는다.
        RelationshipType relationship =
                relationshipType != null ? relationshipType : RelationshipType.INTERNAL;

        double finalRiskScore = riskScore * relationship.riskMultiplier();

        // 규칙 기반 등급 판정
        String ruleRiskLevel = "SAFE";
        if (finalRiskScore >= 0.7) ruleRiskLevel = "DANGER";
        else if (finalRiskScore >= 0.3) ruleRiskLevel = "CAUTION";

        // 규칙은 사전에 등록된 키워드만 잡으므로, 사전에 없는 완곡 거절은 AI 쪽이 더 잘 본다.
        // 어느 한쪽이라도 위험하다고 보면 위험한 것으로 처리한다(둘 중 높은 등급 채택).
        String finalRiskLevel = moreSevere(ruleRiskLevel, aiResponse.riskAnalysis().riskLevel());

        // 카테고리별 적합도 판정
        // 상황 따라 Summary 보정
        String category = aiResponse.category();
        String originalSummary = aiResponse.evaluation().summary();
        String adaptiveSummary = originalSummary;

        if ("INTERVIEW".equals(category) && finalTotal < 90) {
            adaptiveSummary = "[주의: 면접 상황] " + originalSummary + " (면접에서는 더 높은 수준의 경어가 요구됩니다.)";
        } else if ("INTERNAL_CHAT".equals(category) && finalTotal >= 70) {
            adaptiveSummary = "[우수: 사내 채팅] " + originalSummary + " (사내 소통으로써 적절한 표현입니다.)";
        }

        // redFlags 는 규칙 탐지분과 AI 탐지분을 합친다.
        // (예전에는 규칙이 아무것도 못 잡으면 AI 목록만 남아서
        //  riskLevel=SAFE 인데 redFlags 에는 위험 신호가 나열되는 모순이 생겼다)
        Set<String> mergedRedFlags = new LinkedHashSet<>(detectedRedFlags);
        if (aiResponse.riskAnalysis().redFlags() != null) {
            mergedRedFlags.addAll(aiResponse.riskAnalysis().redFlags());
        }

        if (!finalRiskLevel.equalsIgnoreCase(aiResponse.riskAnalysis().riskLevel())) {
            adjustments.add(new ScoreAdjustmentDTO("riskLevel",
                    aiResponse.riskAnalysis().riskLevel(), finalRiskLevel,
                    riskReason(detectedRedFlags, aiResponse.riskAnalysis().riskLevel())));
        }

        RiskAnalysisDTO validatedRisk = new RiskAnalysisDTO(
                finalRiskLevel,
                List.copyOf(mergedRedFlags),
                aiResponse.riskAnalysis().copingStrategy()
        );

        // 최종적으로 조정된 값 담은 DTO 반환
        return new NuanceResponseDTO(
                finalTotal,
                category,
                new MetricsDTO(p, i, e),
                new EvaluationDTO(adaptiveSummary, aiResponse.evaluation().keigoCheck(), aiResponse.evaluation().cushionPhraseCheck()),
                aiResponse.feedback(),
                sanitizeSuggestions(aiResponse.suggestions()),
                aiResponse.sentiment(),
                validatedRisk,
                List.copyOf(adjustments),
                sanitizeSmartReplies(aiResponse.smartReplies())
        );
    }

    // 사용자가 그대로 복사해 보내는 두 필드(suggestions[].text, smartReplies[].content)는
    // 순수 일본어여야 한다. 모델은 두 가지 방식으로 이를 어긴다 —
    //   1) "가르쳐(教えて)" 처럼 외국어 낱말에 괄호로 일본어를 덧붙여 스스로 고치거나,
    //   2) "予算や schedule 面で" 처럼 일본어 낱말 자리를 외국어가 그냥 차지한다.
    // 1번은 괄호를 펴서 살리고, 2번은 기계적으로 되살릴 방법이 없으므로 그 항목을 버린다.
    // 깨진 문장을 그대로 보여 주는 것보다 개수가 줄어드는 편이 낫다(프론트가 빈 목록을 감당한다).

    private List<SuggestionDTO> sanitizeSuggestions(List<SuggestionDTO> suggestions) {
        if (suggestions == null) return List.of();

        List<SuggestionDTO> kept = new ArrayList<>();
        for (SuggestionDTO suggestion : suggestions) {
            String text = JapaneseOutputSanitizer.unwrapGlosses(suggestion.text());
            if (JapaneseOutputSanitizer.containsForeignWord(text)) {
                log.warn("일본어 대안 문장에 외국어가 남아 제외한다: {}", text);
                continue;
            }
            kept.add(new SuggestionDTO(text, suggestion.level()));
        }
        return List.copyOf(kept);
    }

    private List<SmartReplyDTO> sanitizeSmartReplies(List<SmartReplyDTO> smartReplies) {
        if (smartReplies == null) return List.of();

        List<SmartReplyDTO> kept = new ArrayList<>();
        for (SmartReplyDTO reply : smartReplies) {
            String content = JapaneseOutputSanitizer.unwrapGlosses(reply.content());
            if (JapaneseOutputSanitizer.containsForeignWord(content)) {
                log.warn("추천 답장에 외국어가 남아 제외한다: {}", content);
                continue;
            }
            kept.add(new SmartReplyDTO(reply.scenario(), content, reply.description(), reply.nuanceLevel()));
        }
        return List.copyOf(kept);
    }

    /** 배점. 프롬프트의 Scoring Criteria 와 프론트의 막대 길이가 같은 숫자를 쓴다. */
    static final int POLITENESS_MAX = 40;
    static final int INDIRECTNESS_MAX = 30;
    static final int ETIQUETTE_MAX = 30;

    /** 배점 밖의 값을 끝으로 당긴다. 당겼으면 그렇다고 적는다 — 적지 않은 조정은 설명할 수 없다. */
    private static int withinScale(String metric, int value, int max, List<ScoreAdjustmentDTO> adjustments) {
        int bounded = Math.clamp(value, 0, max);
        if (bounded != value) {
            adjustments.add(new ScoreAdjustmentDTO(metric, String.valueOf(value), String.valueOf(bounded),
                    "AI 가 배점(0~" + max + ") 밖의 점수를 주어 범위 안으로 맞췄습니다."));
        }
        return bounded;
    }

    /**
     * 등급이 바뀐 이유. 예전에는 규칙이 아무것도 찾지 못하면 늘 "더 위험한 쪽을 택했다" 고 적었는데,
     * 모델이 "LOW" 처럼 모르는 값을 줘서 SAFE 로 읽은 경우에는 더 위험한 쪽을 택한 일이 없다.
     */
    private String riskReason(List<String> detectedRedFlags, String modelRiskLevel) {
        if (!detectedRedFlags.isEmpty()) {
            return "거절 신호를 찾았습니다: " + String.join(", ", detectedRedFlags);
        }
        if (!RISK_SEVERITY.containsKey(modelRiskLevel == null ? "" : modelRiskLevel.toUpperCase())) {
            return "AI 가 알 수 없는 등급을 돌려줘 규칙 판정을 따랐습니다.";
        }
        return "두 판정 중 더 위험한 쪽을 택했습니다.";
    }

    private static final Map<String, Integer> RISK_SEVERITY = Map.of("SAFE", 0, "CAUTION", 1, "DANGER", 2);

    /** 두 등급 중 더 위험한 쪽을 고른다. 알 수 없는 값은 SAFE 로 본다. */
    private String moreSevere(String a, String b) {
        int left = RISK_SEVERITY.getOrDefault(a == null ? "" : a.toUpperCase(), 0);
        int right = RISK_SEVERITY.getOrDefault(b == null ? "" : b.toUpperCase(), 0);
        return left >= right ? normalize(a) : normalize(b);
    }

    private String normalize(String level) {
        String upper = level == null ? "" : level.toUpperCase();
        return RISK_SEVERITY.containsKey(upper) ? upper : "SAFE";
    }
}