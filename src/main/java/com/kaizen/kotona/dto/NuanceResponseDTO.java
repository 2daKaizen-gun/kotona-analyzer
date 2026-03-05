package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NuanceResponseDTO(
        @JsonProperty("totalScore")
        // 단일 책임 원칙, 확장성
        int totalScore, // 0 ~ 100
        String category, // EMAIL, INTERVIEW, MEETING, CASUAL_CHAT 등
        MetricsDTO metrics,
        EvaluationDTO evaluation,
        FeedbackDTO feedback,
        List<SuggestionDTO> suggestions,
        SentimentDTO sentiment, // 감정 및 본심 분석 데이터
        RiskAnalysisDTO riskAnalysis, // 위기 감지 데이터
        List<SmartReplyDTO> smartReplies // 상황별 추천 답장 리스트
) {}

