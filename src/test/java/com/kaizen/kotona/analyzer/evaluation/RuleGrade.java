package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.dto.RelationshipType;
import com.kaizen.kotona.analyzer.service.JapaneseTokenService;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;

import java.util.Map;
import java.util.Set;

/**
 * 규칙 계층이 한 문장에 매기는 등급. {@code AnalysisValidator} 와 같은 계산이다.
 *
 * <p>두 테스트가 같은 계산을 필요로 한다 — 규칙이 라벨보다 위험하게 읽지 않는지 보는 쪽과,
 * 사용자가 실제로 보는 등급(규칙과 모델 중 더 위험한 쪽)을 보는 쪽이다. 각자 복사해 두면
 * 한쪽만 고쳐져도 둘 다 초록불로 남는다.
 */
final class RuleGrade {

    /** 등급의 서열. 두 판정 중 더 위험한 쪽을 고르는 데 쓴다. */
    static final Map<String, Integer> SEVERITY = Map.of("SAFE", 0, "CAUTION", 1, "DANGER", 2);

    private RuleGrade() {
    }

    /** 사전에 걸린 거절 신호의 합. 낱말과 구를 함께 본다. */
    static double score(JapaneseTokenService tokenService, String text) {
        Set<String> lemmas = tokenService.baseForms(text);
        double fromLemmas = EtiquetteConstants.SOFT_REJECTION_SIGNALS.entrySet().stream()
                .filter(entry -> EtiquetteConstants.signalMatches(entry, lemmas))
                .mapToDouble(entry -> entry.getValue().weight())
                .sum();
        double fromPhrases = EtiquetteConstants.SOFT_REJECTION_PHRASES.entrySet().stream()
                .filter(entry -> text.contains(entry.getKey()))
                .mapToDouble(entry -> entry.getValue().weight())
                .sum();
        return fromLemmas + fromPhrases;
    }

    /** 점수에 관계 배수를 곱하고 같은 경계로 자른다. */
    static String of(double score, RelationshipType relationship) {
        double weighted = score * relationship.riskMultiplier();
        if (weighted >= 0.7) {
            return "DANGER";
        }
        return weighted >= 0.3 ? "CAUTION" : "SAFE";
    }

    /** 최종 등급은 둘 중 더 위험한 쪽이다. 검증기가 하는 것과 같다. */
    static String moreSevere(String one, String other) {
        return SEVERITY.get(one) >= SEVERITY.get(other) ? one : other;
    }
}
