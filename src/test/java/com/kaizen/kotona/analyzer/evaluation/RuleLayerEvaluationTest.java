package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.service.JapaneseTokenService;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙 계층을 라벨된 문장과 맞대어 본다. 모델을 부르지 않으므로 CI 에서 매번 돈다.
 *
 * <p>여기서 재는 것은 "모델이 옳은가" 가 아니라 "모델 점수를 고치는 우리 규칙이 옳은가" 다.
 * 그 규칙은 사전 몇 줄로 되어 있고, 사전에 없는 표현은 전부 "없다" 로 판정한다 —
 * 그 대가가 얼마인지 숫자로 보려고 만들었다.
 */
class RuleLayerEvaluationTest {

    private final JapaneseTokenService tokenService = new JapaneseTokenService();
    private final EvaluationSet set = EvaluationSet.load();

    @Test
    @DisplayName("정중체 판정이 라벨과 일치한다")
    void politeFormDetectionMatchesTheLabels() {
        List<String> wrong = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            boolean detected = tokenService.hasPoliteEnding(row.text());
            if (detected != row.politeForm()) {
                wrong.add("%s: 라벨 %s, 판정 %s — %s".formatted(
                        row.id(), row.politeForm(), detected, row.text()));
            }
        }
        report("정중체", set.rows().size(), wrong);
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("쿠션어 판정이 라벨과 일치한다")
    void cushionDetectionMatchesTheLabels() {
        // 사전에 없다는 이유로 정중한 문장이 감점되던 자리다. 어긋나면 사전을 늘려야 한다.
        List<String> wrong = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            boolean detected = tokenService.baseForms(row.text()).stream()
                    .anyMatch(EtiquetteConstants.CUSHION_LEMMAS::contains)
                    || EtiquetteConstants.CUSHION_PHRASES.stream().anyMatch(row.text()::contains);
            if (detected != row.cushion()) {
                wrong.add("%s: 라벨 %s, 판정 %s — %s".formatted(
                        row.id(), row.cushion(), detected, row.text()));
            }
        }
        report("쿠션어", set.rows().size(), wrong);
        assertThat(wrong).isEmpty();
    }

    @Test
    @DisplayName("거절 신호 사전이 위험한 문장을 놓치지 않는다")
    void softRejectionDictionaryCatchesTheRiskyOnes() {
        // 규칙은 사전에 있는 낱말만 본다. 모델이 두 번째 눈이지만, 규칙이 통째로
        // 놓치는 축이 있으면 그 축은 사실상 모델 한 쪽에만 기대는 것이다.
        List<String> missed = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            if (row.risk().equals("SAFE")) {
                continue;
            }
            Set<String> lemmas = tokenService.baseForms(row.text());
            if (EtiquetteConstants.SOFT_REJECTION_SIGNALS.entrySet().stream()
                    .noneMatch(signal -> EtiquetteConstants.signalMatches(signal, lemmas))) {
                missed.add("%s (%s) — %s".formatted(row.id(), row.risk(), row.text()));
            }
        }
        report("거절 신호", (int) set.rows().stream().filter(r -> !r.risk().equals("SAFE")).count(), missed);
        assertThat(missed).isEmpty();
    }

    @Test
    @DisplayName("거절 신호 사전이 멀쩡한 문장을 위험하다고 하지 않는다")
    void softRejectionDictionaryDoesNotFlagTheSafeOnes() {
        // 「確認のうえ改めてご連絡いたします」 처럼 정상적인 절차를 거절로 읽으면,
        // 사용자는 아무 문제 없는 답장을 고치려 들게 된다.
        List<String> falseAlarms = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            if (!row.risk().equals("SAFE")) {
                continue;
            }
            Set<String> lemmas = tokenService.baseForms(row.text());
            double score = EtiquetteConstants.SOFT_REJECTION_SIGNALS.entrySet().stream()
                    .filter(entry -> EtiquetteConstants.signalMatches(entry, lemmas))
                    .mapToDouble(entry -> entry.getValue().weight())
                    .sum();
            // 사외는 1.2 배가 곱해지므로 그 상태로 CAUTION(0.3) 을 넘는지 본다.
            if (score * row.relationship().riskMultiplier() >= 0.3) {
                falseAlarms.add("%s: 점수 %.2f — %s".formatted(row.id(), score, row.text()));
            }
        }
        report("오경보", (int) set.rows().stream().filter(r -> r.risk().equals("SAFE")).count(), falseAlarms);
        assertThat(falseAlarms).isEmpty();
    }

    private void report(String axis, int total, List<String> wrong) {
        System.out.printf("%n[%s] %d건 중 %d건 일치 (%.0f%%)%n",
                axis, total, total - wrong.size(), 100.0 * (total - wrong.size()) / total);
        wrong.forEach(line -> System.out.println("   어긋남: " + line));
    }
}
