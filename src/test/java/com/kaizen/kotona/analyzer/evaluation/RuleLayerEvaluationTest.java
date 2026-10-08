package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.service.JapaneseTokenService;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * 재현율의 바닥선.
     *
     * <p>100% 를 요구하지 않는다. 사전과 형태소로 임의의 일본어를 전부 잡는 것은 가능하지
     * 않고, 요구하면 테스트셋에 맞춰 사전을 늘리는 일(과적합)밖에 남지 않는다. 규칙은 모델의
     * 두 번째 눈이고, 최종 등급은 둘 중 더 위험한 쪽을 택하므로 규칙이 놓친 것은 모델이 잡는다.
     *
     * <p>대신 <b>내려가지 않는 것</b>을 지킨다. 지금 13건 중 12건이고, 놓친 하나는
     * 「状況が変わりましたらお声がけいたします」 다 — 같은 표현이 권유로도 쓰여 사전으로는
     * 가릴 수 없다({@code EtiquetteConstants.SOFT_REJECTION_PHRASES} 주석 참고).
     */
    private static final double RECALL_FLOOR = 12.0 / 13.0;

    @Test
    @DisplayName("거절 신호 사전의 재현율이 기준 아래로 내려가지 않는다")
    void softRejectionDictionaryCatchesTheRiskyOnes() {
        // 규칙은 사전에 있는 낱말만 본다. 모델이 두 번째 눈이지만, 규칙이 통째로
        // 놓치는 축이 있으면 그 축은 사실상 모델 한 쪽에만 기대는 것이다.
        List<String> missed = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            if (row.risk().equals("SAFE")) {
                continue;
            }
            if (!detectsRefusal(row.text())) {
                missed.add("%s (%s) — %s".formatted(row.id(), row.risk(), row.text()));
            }
        }
        int risky = (int) set.rows().stream().filter(r -> !r.risk().equals("SAFE")).count();
        report("거절 신호", risky, missed);
        double recall = (double) (risky - missed.size()) / risky;
        assertThat(recall)
                .as("재현율이 기준(%.0f%%) 아래로 내려갔다. 사전이 좁아졌거나 어려운 문장이 늘었다 — 어느 쪽인지 보고 결정할 것",
                        RECALL_FLOOR * 100)
                .isGreaterThanOrEqualTo(RECALL_FLOOR);
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
            double score = refusalScore(row.text());
            // 사외는 1.2 배가 곱해지므로 그 상태로 CAUTION(0.3) 을 넘는지 본다.
            if (score * row.relationship().riskMultiplier() >= 0.3) {
                falseAlarms.add("%s: 점수 %.2f — %s".formatted(row.id(), score, row.text()));
            }
        }
        report("오경보", (int) set.rows().stream().filter(r -> r.risk().equals("SAFE")).count(), falseAlarms);
        assertThat(falseAlarms).isEmpty();
    }

    /**
     * 규칙이 매긴 등급이 라벨보다 위험해지지 않는다.
     *
     * <p>지금까지 이 축은 두 가지만 봤다 — 위험한 문장을 놓치지 않는가(재현율), 멀쩡한 문장을
     * 위험하다고 하지 않는가(오경보). 그 사이가 비어 있었다: CAUTION 라벨을 DANGER 로 올려
     * 읽는 것은 오경보 검사에 걸리지 않는다. SAFE 가 아니기 때문이다.
     *
     * <p>그 틈에 실제로 하나가 있었다. 「少し考えておきます」(EXTERNAL) 는 라벨이 CAUTION
     * 인데 규칙은 DANGER 였다 — 考える+おく 0.6 에 사외 1.2 가 곱해져 0.72, 경계는 0.7.
     * 라벨은 문장만 보고 쓰였고, 자기 자신이 들고 있는 relationship 이 뜻하는 배수를 적용하지
     * 않았다. 2026-10-08 에 모델이 그 문장을 DANGER 로 답하면서 드러났고, 그래서 라벨을 고쳤다.
     *
     * <p>이 방향만 단언한다. 반대 방향(라벨이 더 위험한데 규칙이 못 따라가는 것)은 사전의
     * 한계이고 재현율이 이미 재고 있으며, 최종 등급은 모델과 규칙 중 더 위험한 쪽을 택한다.
     */
    @Test
    @DisplayName("규칙이 라벨보다 위험하게 읽는 문장이 없다")
    void ruleGradeNeverOutrunsTheLabel() {
        List<String> tooSevere = new ArrayList<>();
        for (EvaluationSet.Row row : set.rows()) {
            String ruleGrade = gradeFromRules(row);
            if (SEVERITY.get(ruleGrade) > SEVERITY.get(row.risk())) {
                tooSevere.add("%s: 라벨 %s, 규칙 %s (점수 %.2f × %s %.1f) — %s".formatted(
                        row.id(), row.risk(), ruleGrade,
                        refusalScore(row.text()), row.relationship(), row.relationship().riskMultiplier(),
                        row.text()));
            }
        }
        report("등급 과잉", set.rows().size(), tooSevere);
        assertThat(tooSevere)
                .as("규칙이 라벨보다 높은 등급을 매겼다 — 가중치가 과한 것이거나 라벨이 배수를 빼먹은 것이다")
                .isEmpty();
    }

    private static final Map<String, Integer> SEVERITY = Map.of("SAFE", 0, "CAUTION", 1, "DANGER", 2);

    /** 검증기와 같은 계산. 점수에 관계 배수를 곱하고 같은 경계로 자른다. */
    private String gradeFromRules(EvaluationSet.Row row) {
        double score = refusalScore(row.text()) * row.relationship().riskMultiplier();
        if (score >= 0.7) {
            return "DANGER";
        }
        return score >= 0.3 ? "CAUTION" : "SAFE";
    }

    /** 낱말 사전과 구 사전을 함께 본다. 검증기와 같은 판정이어야 평가가 의미를 갖는다. */
    private boolean detectsRefusal(String text) {
        return refusalScore(text) > 0;
    }

    private double refusalScore(String text) {
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

    private void report(String axis, int total, List<String> wrong) {
        System.out.printf("%n[%s] %d건 중 %d건 일치 (%.0f%%)%n",
                axis, total, total - wrong.size(), 100.0 * (total - wrong.size()) / total);
        wrong.forEach(line -> System.out.println("   어긋남: " + line));
    }
}
