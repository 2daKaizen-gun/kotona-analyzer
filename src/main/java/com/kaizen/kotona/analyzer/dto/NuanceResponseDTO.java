package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

/**
 * /analyze 응답 스키마.
 * Gemini responseSchema 에 넘길 JSON 스키마가 이 record 트리에서 그대로 파생되므로,
 * 프롬프트에 스키마를 중복 기술하지 않는다. 필드 의미는 @JsonPropertyDescription 이 전달한다.
 */
public record NuanceResponseDTO(
        @JsonPropertyDescription("KOTONA 뉘앙스 총점. 0~100 사이의 정수.")
        int totalScore,

        @JsonPropertyDescription("커뮤니케이션 분류. \"EMAIL\", \"INTERVIEW\", \"MEETING\", \"INTERNAL_CHAT\", \"CASUAL\" 중 하나.")
        String category,

        @JsonPropertyDescription("항목별 점수 상세.")
        MetricsDTO metrics,

        @JsonPropertyDescription("종합 평가.")
        EvaluationDTO evaluation,

        @JsonPropertyDescription("개선 피드백.")
        FeedbackDTO feedback,

        @JsonPropertyDescription("개선된 대안 문장 2~3개.")
        List<SuggestionDTO> suggestions,

        @JsonPropertyDescription("감정 및 본심 분석.")
        SentimentDTO sentiment,

        @JsonPropertyDescription("비즈니스 리스크 감지 결과.")
        RiskAnalysisDTO riskAnalysis,

        @JsonPropertyDescription("상황별 추천 답장 3개.")
        List<SmartReplyDTO> smartReplies
) {}
