package com.kaizen.kotona.analyzer.evaluation;

import com.kaizen.kotona.analyzer.dto.RelationshipType;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.List;

/**
 * 라벨된 평가 문장. 점수가 기댈 유일한 바깥 기준이다.
 *
 * <p>라벨은 저자가 붙였고, 행마다 그렇게 붙인 이유({@code basis})를 함께 적는다.
 * 원어민 검수를 거치지 않았으므로 "정답" 이 아니라 "근거를 밝힌 기준" 이다 —
 * 그 한계는 README 에 적어 둔다. 숨기면 숫자가 실제보다 세 보인다.
 *
 * <p>세 축을 따로 둔다. 정중체 여부와 쿠션어 유무는 문장을 보면 판정할 수 있고,
 * 리스크는 거절 신호의 유무다. 「申し訳ございませんが、難しい状況です」 처럼
 * 쿠션어를 쓰면서 거절하는 문장이 있으므로 한 축으로 묶을 수 없다.
 */
public record EvaluationSet(List<Row> rows) {

    public record Row(
            String id,
            String text,
            RelationshipType relationship,
            boolean politeForm,
            boolean cushion,
            String risk,
            String basis) {
    }

    public static EvaluationSet load() {
        try (InputStream in = EvaluationSet.class.getResourceAsStream("/evaluation/business-sentences.json")) {
            if (in == null) {
                throw new IllegalStateException("평가셋 리소스를 찾을 수 없다");
            }
            return new EvaluationSet(new ObjectMapper().readValue(in, new tools.jackson.core.type.TypeReference<List<Row>>() {}));
        } catch (Exception e) {
            throw new IllegalStateException("평가셋을 읽지 못했다", e);
        }
    }
}
