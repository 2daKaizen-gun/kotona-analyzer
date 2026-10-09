package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.service.JapaneseTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자가 실제로 보는 등급을 재 본다. 모델을 부르지 않으므로 CI 에서 매번 돈다.
 *
 * <p>지금까지 두 쪽을 따로 재 왔다 — 규칙이 라벨과 맞는지, 모델이 라벨과 맞는지. 그런데
 * 화면에 뜨는 등급은 둘 중 <b>더 위험한 쪽</b>이고, 그 합성은 아무도 재지 않았다. 재지 않으면
 * 놓치는 것이 있다: 규칙의 오경보가 0 건이어도 모델이 올려 읽으면 그 경고는 그대로 사용자에게
 * 간다. 「社内で確認のうえ、改めてご連絡いたします」 가 그렇다 — 규칙은 SAFE, 모델은 CAUTION,
 * 사용자는 CAUTION 을 본다. 아무 문제 없는 답장을 고치러 가는 것이다.
 *
 * <p>모델에게 물어 받은 답은 {@code model-answers.json} 에 남아 있으므로, 호출 없이 계산할 수
 * 있다. 그래서 이 숫자는 쿼터와 무관하게 매 푸시마다 다시 계산된다.
 */
class ProductGradeTest {

    private final JapaneseTokenService tokenService = new JapaneseTokenService();

    /**
     * 지금 과하게 올려 읽는 문장들.
     *
     * <p>비워 두지 않고 이름을 적어 둔다 — 새로 하나가 늘면 테스트가 깨지고, 그때 이것이
     * 설계의 대가인지 고칠 수 있는 결함인지 보게 된다. 셋 다 모델이 라벨보다 위험하게 읽은
     * 경우이고, 라벨 쪽 근거에 "실무에서는 거절로 읽히지만 문면상 보류" 라고 적어 둔 문장이
     * 둘 섞여 있다. 그 둘은 정의의 차이에 가깝고, {@code reject-05} 는 그렇지 않다 —
     * 프롬프트가 바로 그 문장을 SAFE 의 예로 적어 두었는데도 모델이 CAUTION 으로 답했다.
     */
    private static final Set<String> KNOWN_OVER_ESCALATIONS = Set.of("reject-05", "real-02", "real-05");

    @Test
    @DisplayName("합성 등급이 라벨보다 위험해지는 문장은 알고 있는 세 건뿐이다")
    void theGradeTheUserSeesIsNotMoreSevereThanTheLabelExceptWhereWeKnowItIs() {
        Map<String, String> modelAnswers = EvaluationLog.load().recorded("risk").stream()
                .collect(Collectors.toMap(EvaluationLog.Answer::id, EvaluationLog.Answer::value));

        List<String> overEscalated = new ArrayList<>();
        List<String> missed = new ArrayList<>();
        int compared = 0;
        int agreed = 0;
        int modelAgreed = 0;
        int ruleAgreed = 0;

        for (EvaluationSet.Row row : EvaluationSet.load().rows()) {
            String modelGrade = modelAnswers.get(row.id());
            if (modelGrade == null) {
                continue; // 아직 답을 받지 못한 문장
            }
            compared++;
            String ruleGrade = RuleGrade.of(RuleGrade.score(tokenService, row.text()), row.relationship());
            String product = RuleGrade.moreSevere(ruleGrade, modelGrade);

            if (modelGrade.equals(row.risk())) {
                modelAgreed++;
            }
            if (ruleGrade.equals(row.risk())) {
                ruleAgreed++;
            }
            if (product.equals(row.risk())) {
                agreed++;
            } else if (RuleGrade.SEVERITY.get(product) > RuleGrade.SEVERITY.get(row.risk())) {
                overEscalated.add("%s: 라벨 %s → 규칙 %s + 모델 %s = %s — %s".formatted(
                        row.id(), row.risk(), ruleGrade, modelGrade, product, row.text()));
            } else {
                missed.add("%s: 라벨 %s → 규칙 %s + 모델 %s = %s — %s".formatted(
                        row.id(), row.risk(), ruleGrade, modelGrade, product, row.text()));
            }
        }

        System.out.printf("%n=== 답을 받은 %d 문장에서 ===%n", compared);
        System.out.printf("  규칙만:   %d건 일치%n", ruleAgreed);
        System.out.printf("  모델만:   %d건 일치%n", modelAgreed);
        System.out.printf("  합성(실제 화면): %d건 일치, 과잉 %d건, 놓침 %d건%n",
                agreed, overEscalated.size(), missed.size());
        overEscalated.forEach(line -> System.out.println("   과잉: " + line));
        missed.forEach(line -> System.out.println("   놓침: " + line));

        assertThat(compared)
                .as("기록된 답이 있어야 이 측정이 의미를 갖는다")
                .isPositive();
        assertThat(overEscalated.stream().map(line -> line.split(":")[0]).toList())
                .as("과하게 올려 읽는 문장이 늘었다 — 설계의 대가인지 고칠 결함인지 보고 결정할 것")
                .containsExactlyInAnyOrderElementsOf(KNOWN_OVER_ESCALATIONS);
        assertThat(missed)
                .as("규칙도 모델도 라벨만큼 위험하게 읽지 못한 문장 — 둘 다 놓친 것이므로 사용자는 경고를 못 받는다")
                .isEmpty();
    }
}
