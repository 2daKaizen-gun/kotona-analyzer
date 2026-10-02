package com.kaizen.kotona.analyzer.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원어민 검수 문서가 실제 평가셋과 같은 내용을 담고 있는지.
 *
 * <p>검수는 사람이 하는 일이라 한 번 보내고 끝나지 않는다 — 라벨이 바뀌면 문서도 바뀌어야
 * 하는데, 종이 쪽은 조용히 낡는다. 낡은 문서를 보내면 이미 고친 문장을 다시 검수받게 된다.
 */
class ReviewDocumentTest {

    private static final Path DOC = Path.of("docs/NATIVE_REVIEW.md");

    @Test
    @DisplayName("검수 문서가 평가셋의 모든 문장과 순서 쌍을 담고 있다")
    void theReviewDocumentCoversEverythingWeWantReviewed() throws Exception {
        String doc = Files.readString(DOC);
        List<String> missing = new ArrayList<>();

        for (EvaluationSet.Row row : EvaluationSet.load().rows()) {
            if (!doc.contains(row.text())) {
                missing.add("문장: " + row.text());
            }
        }
        for (EvaluationSet.OrderingPair pair : EvaluationSet.orderingPairs()) {
            if (!doc.contains(pair.lower()) || !doc.contains(pair.higher())) {
                missing.add("순서 쌍: " + pair.id());
            }
        }

        assertThat(missing)
                .as("docs/NATIVE_REVIEW.md 를 다시 만들어야 한다 — 평가셋에 있는데 문서에 없는 항목")
                .isEmpty();
    }

    @Test
    @DisplayName("검수 문서는 확인이 필요한 축만 묻는다")
    void theReviewDocumentAsksOnlyAboutWhatNeedsAHuman() throws Exception {
        // 정중체는 문화청 기준으로 판정되므로 사람에게 물을 일이 아니다. 물으면 시간을 낭비시킨다.
        String doc = Files.readString(DOC);

        assertThat(doc).contains("クッション言葉", "確答");
        assertThat(doc).contains("文法で決まる部分は確認不要");
        assertThat(doc).contains("bunka.go.jp");
    }
}
