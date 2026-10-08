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

    /**
     * 시트에 적힌 판정이 라벨과 같은지.
     *
     * <p>이 문서는 문장마다 우리 판정을 함께 적어 둔다. 그런데 지금까지 검사한 것은 문장이
     * 들어 있는지뿐이어서, 라벨을 고쳐도 시트의 판정 칸은 조용히 옛 값으로 남았다 —
     * 2026-10-08 에 reject-03 을 CAUTION 에서 DANGER 로 고쳤을 때 실제로 그랬다.
     * 틀린 판정을 적어 보내면 검수자는 틀린 것을 확인해 준다.
     */
    @Test
    @DisplayName("시트에 적힌 판정이 라벨과 같다")
    void theSheetShowsTheSameVerdictAsTheLabels() throws Exception {
        List<String> drifted = new ArrayList<>();
        List<String> lines = Files.readAllLines(DOC);

        for (EvaluationSet.Row row : EvaluationSet.load().rows()) {
            String line = lines.stream()
                    .filter(candidate -> candidate.startsWith("|") && candidate.contains("| " + row.text() + " |"))
                    .findFirst()
                    .orElse(null);
            if (line == null) {
                continue; // 문장 누락은 위 테스트가 잡는다
            }
            String[] cells = line.split("\\|");
            String cushionCell = cells[4].trim();
            String riskCell = cells[5].trim();
            if (!riskCell.startsWith(row.risk())) {
                drifted.add("%s: 라벨 %s, 시트 %s".formatted(row.id(), row.risk(), riskCell));
            }
            String expectedCushion = row.cushion() ? "あり" : "なし";
            if (!cushionCell.equals(expectedCushion)) {
                drifted.add("%s: 쿠션어 라벨 %s, 시트 %s".formatted(row.id(), expectedCushion, cushionCell));
            }
        }

        assertThat(drifted)
                .as("docs/NATIVE_REVIEW.md 의 판정 칸이 라벨과 어긋난다 — 이대로 보내면 틀린 판정을 검수받는다")
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
