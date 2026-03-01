package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NuanceResponseDTO(
        @JsonProperty("totalScore")
        // 단일 책임 원칙, 확장성
        int totalScore, // 0 ~ 100
        MetricsDTO metrics,
        EvaluationDTO evaluation,
        FeedbackDTO feedback,
        List<SuggestionDTO> suggestions,
        SentimentDTO sentiment // 감정 및 본심 분석 데이터
) {}

public record SentimentDTO(
    String polarity, // Positive, Neutral, Negative
    double confidence, // 0.0 ~ 1.0
    HonneDTO honne // 속마음 분석 결과
) {}

public record HonneDTO(
    String tatemae, // 표면적 의미
    String trueIntent, // 본심
    String actionItem // 사용자 행동 가이드
) {}
