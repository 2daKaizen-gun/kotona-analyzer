package com.kaizen.kotona.analyzer.service;

import com.kaizen.kotona.analyzer.dto.EvaluationDTO;
import com.kaizen.kotona.analyzer.dto.MetricsDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.dto.RiskAnalysisDTO;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AnalysisValidator {
    //AI 점수와 형태소 분석 및 규칙 기반 데이터 교차 검증
    public NuanceResponseDTO validate(NuanceResponseDTO aiResponse, String cleanInput, String relationshipType, boolean hasPoliteEnding) {
        // 기존 Metrics 가져오기
        int p = aiResponse.metrics().politeness();   // Max 40
        int i = aiResponse.metrics().indirectness(); // Max 30
        int e = aiResponse.metrics().etiquette();    // Max 30

        // 1. Politeness 검증 (경어 사용 여부)
        if (p >= 30 && !hasPoliteEnding) {
            p = Math.max(0, p - 10); // 10점 페널티
        }

        // 2. Etiquette 검증 (쿠션어 사용 여부)
        boolean hasCushion = EtiquetteConstants.CUSHION_PHRASES.stream()
                .anyMatch(cleanInput::contains);
        if (e >= 20 && !hasCushion) {
            e = Math.max(0, e - 10); // 10점 페널티
        }

        // 3. Indirectness 검증 (완곡 어미 사용 여부)
        boolean hasIndirect = EtiquetteConstants.INDIRECT_ENDINGS.stream()
                .anyMatch(cleanInput::contains);
        if (i >= 20 && !hasIndirect) {
            i = Math.max(0, i - 5); // 5점 페널티
        }

        // 최종 총점 재계산 (합계가 100을 넘지 않도록 보정)
        int finalTotal = Math.min(100, p + i + e);

        // 리스크 가중치 산출 로직
        // 공식: Risk Score = Σ(Signal_i * W_i)
        double riskScore = 0.0;
        List<String> detectedRedFlags = new ArrayList<>();

        for (Map.Entry<String, SoftRejectionSignal> entry : SOFT_REJECTION_SIGNALS.entrySet()) {
            if (cleanInput.contains(entry.getKey())) {
                riskScore += entry.getValue().weight();
                detectedRedFlags.add(entry.getValue().description());
            }
        }

        // 컨텍스트 가중치($W$) 적용
        double multiplier = switch (relationshipType != null ? relationshipType : "INTERNAL") {
            case "EXTERNAL" -> 1.2;  // 사외 관계는 위험도 증폭
            case "INTERVIEW" -> 1.5; // 면접은 치명적
            default -> 1.0;          // 사내는 기본값
        };

        double finalRiskScore = riskScore * multiplier;

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
                aiResponse.suggestions(),
                aiResponse.sentiment(),
                validatedRisk,
                aiResponse.smartReplies()
        );
    }

    /** 소프트 리젝션 키워드와 가중치. 프롬프트의 Risk Detection Guide 와 같은 목록을 본다. */
    private record SoftRejectionSignal(double weight, String description) {
    }

    private static final Map<String, SoftRejectionSignal> SOFT_REJECTION_SIGNALS = new LinkedHashMap<>() {{
        put("難しい", new SoftRejectionSignal(0.8, "'어렵다(難しい)' 시그널 감지"));
        put("考えておく", new SoftRejectionSignal(0.6, "'생각해 보겠다'는 모호한 응답"));
        put("検討", new SoftRejectionSignal(0.5, "'검토(検討)' 시그널 감지"));
        // 確認 은 정중한 표현에서도 흔히 쓰이므로 단독으로는 CAUTION 이 되지 않게 낮게 잡는다.
        put("確認", new SoftRejectionSignal(0.2, "'확인(確認)' 후 회신 — 즉답 회피 가능성"));
    }};

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