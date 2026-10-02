package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.dto.*;
import com.kaizen.kotona.analyzer.service.AnalysisValidator;
import com.kaizen.kotona.analyzer.service.JapaneseTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 규칙이 문장의 순서를 뒤집지 않는지 본다.
 *
 * <p>지표 세 개(정중도·간접성·에티켓)에는 비교할 정답 점수가 없다 — 「35/40 이 맞다」 고
 * 말해 줄 기준이 없기 때문이다. 하지만 <b>순서</b>에는 정답이 있다. 「よろしく」 가
 * 「よろしくお願い申し上げます」 보다 정중할 수는 없고, 같은 의뢰에 쿠션어를 덧붙였는데
 * 예의 점수가 내려갈 수는 없다.
 *
 * <p>그래서 두 문장에 똑같은 모델 점수를 주고 규칙만 통과시킨다. 규칙이 한쪽만 깎아서
 * 순서를 뒤집으면 여기서 걸린다 — 「恐れ入りますが」 가 사전에 없어 10점 깎이던 버그가
 * 정확히 이 모양이었다.
 */
class RuleLayerOrderingTest {

    private final AnalysisValidator validator = new AnalysisValidator(new JapaneseTokenService());

    @Test
    @DisplayName("규칙은 더 정중한 쪽을 더 낮게 만들지 않는다")
    void theRulesNeverInvertAPair() {
        List<String> inverted = new java.util.ArrayList<>();

        for (EvaluationSet.OrderingPair pair : EvaluationSet.orderingPairs()) {
            // 두 문장에 같은 모델 점수를 준다. 차이가 생긴다면 그것은 전부 규칙이 만든 것이다.
            // 감점 조건(정중 30↑, 간접 20↑, 예의 20↑)을 모두 넘는 값으로 잡아 규칙이 작동하게 한다.
            int lower = axisValue(pair.axis(), validate(pair.lower()));
            int higher = axisValue(pair.axis(), validate(pair.higher()));

            System.out.printf("  %-18s %-12s %2d → %2d   %s%n",
                    pair.id(), pair.axis(), lower, higher,
                    higher >= lower ? "" : "뒤집힘");

            if (higher < lower) {
                inverted.add("%s (%s): 「%s」=%d 가 「%s」=%d 보다 높게 나왔다%n      근거: %s"
                        .formatted(pair.id(), pair.axis(), pair.lower(), lower, pair.higher(), higher, pair.basis()));
            }
        }

        assertThat(inverted).isEmpty();
    }

    private NuanceResponseDTO validate(String text) {
        NuanceResponseDTO model = new NuanceResponseDTO(
                85, "EMAIL",
                new MetricsDTO(35, 25, 25),
                new EvaluationDTO("요약", true, true),
                new FeedbackDTO(List.of(), "해설"),
                List.of(),
                new SentimentDTO("Neutral", 0.9, new HonneDTO("겉", "속", "행동")),
                new RiskAnalysisDTO("SAFE", List.of(), "전략"),
                List.of(),
                List.of());
        // hasPoliteEnding 은 서비스가 형태소 분석으로 채우는 값이라 여기서도 같은 판정을 쓴다.
        boolean polite = new JapaneseTokenService().hasPoliteEnding(text);
        return validator.validate(model, text, RelationshipType.EXTERNAL, polite);
    }

    private int axisValue(String axis, NuanceResponseDTO result) {
        return switch (axis) {
            case "politeness" -> result.metrics().politeness();
            case "indirectness" -> result.metrics().indirectness();
            case "etiquette" -> result.metrics().etiquette();
            default -> throw new IllegalArgumentException("모르는 축: " + axis);
        };
    }
}
