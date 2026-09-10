package com.kaizen.kotona.analyzer.service;

import com.kaizen.kotona.analyzer.dto.EvaluationDTO;
import com.kaizen.kotona.analyzer.dto.FeedbackDTO;
import com.kaizen.kotona.analyzer.dto.HonneDTO;
import com.kaizen.kotona.analyzer.dto.MetricsDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.dto.RiskAnalysisDTO;
import com.kaizen.kotona.analyzer.dto.SentimentDTO;
import com.kaizen.kotona.analyzer.dto.SmartReplyDTO;
import com.kaizen.kotona.analyzer.dto.SuggestionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 일본어 필드 교정이 validate() 를 거쳐 실제로 적용되는지 확인한다.
 * (교정 규칙 자체는 JapaneseOutputSanitizerTest 가 본다)
 */
class AnalysisValidatorSanitizeTest {

    private final AnalysisValidator validator = new AnalysisValidator();

    private static final String INPUT = "お手数ですが、ご確認いただけますでしょうか。";

    @Test
    @DisplayName("추천 답장의 괄호 주석을 펴고, 못 고친 항목은 목록에서 뺀다")
    void repairsSmartRepliesAndDropsWhatItCannotFix() {
        NuanceResponseDTO aiResponse = responseWith(
                List.of(new SuggestionDTO("お 가르쳐(教えて)ください。", "standard")),
                List.of(
                        // 관측된 실패 그대로 — 외국어 낱말 + 괄호 자기 교정
                        new SmartReplyDTO("Clarification", "目安をお 가르쳐(教えて)いただけますか。", "확인용", "Standard"),
                        new SmartReplyDTO("Soft Acceptance", "何卒よろしく communication(お願い申し上げます)。", "수용", "Soft"),
                        // 괄호가 없어 펼 수 없는 혼입 — 그대로 내보낼 수 없다
                        new SmartReplyDTO("Counter-proposal", "ご確認 부탁드립니다。", "대안", "Firm"),
                        new SmartReplyDTO("Clarification", "予算や schedule 面でのご懸念はございますか。", "확인", "Firm")));

        NuanceResponseDTO result = validator.validate(aiResponse, INPUT, "EXTERNAL", true);

        assertThat(result.smartReplies())
                .extracting(SmartReplyDTO::nuanceLevel, SmartReplyDTO::content)
                .containsExactly(
                        tuple("Standard", "目安をお教えていただけますか。"),
                        tuple("Soft", "何卒よろしくお願い申し上げます。"));

        assertThat(result.suggestions())
                .extracting(SuggestionDTO::text)
                .containsExactly("お教えてください。");
    }

    @Test
    @DisplayName("정상 일본어는 손대지 않는다")
    void leavesCleanOutputUntouched() {
        SuggestionDTO suggestion = new SuggestionDTO("ご確認のほどよろしくお願いいたします。", "standard");
        SmartReplyDTO reply = new SmartReplyDTO(
                "Clarification", "〇月〇日（〇）までにSlackへご回答いただけますと幸いです。", "기한 확인", "Firm");

        NuanceResponseDTO result =
                validator.validate(responseWith(List.of(suggestion), List.of(reply)), INPUT, "EXTERNAL", true);

        assertThat(result.suggestions()).containsExactly(suggestion);
        assertThat(result.smartReplies()).containsExactly(reply);
    }

    @Test
    @DisplayName("두 목록이 null 이어도 빈 목록으로 내려간다")
    void tolerlatesNullLists() {
        NuanceResponseDTO result = validator.validate(responseWith(null, null), INPUT, "INTERNAL", true);

        assertThat(result.suggestions()).isEmpty();
        assertThat(result.smartReplies()).isEmpty();
    }

    /** 교정 대상 두 목록 외에는 검증에 영향을 주지 않는 최소 응답. */
    private NuanceResponseDTO responseWith(List<SuggestionDTO> suggestions, List<SmartReplyDTO> smartReplies) {
        return new NuanceResponseDTO(
                90,
                "EMAIL",
                new MetricsDTO(40, 20, 30),
                new EvaluationDTO("정중한 표현입니다.", true, true),
                new FeedbackDTO(List.of(), "쿠션어가 적절합니다."),
                suggestions,
                new SentimentDTO("NEUTRAL", 0.9, new HonneDTO("확인 부탁", "조속한 회신 희망", "회신 대기")),
                new RiskAnalysisDTO("SAFE", List.of(), "회신을 기다립니다."),
                smartReplies);
    }
}
