package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SentimentDTO(
        @JsonPropertyDescription("감정 극성. \"Positive\", \"Neutral\", \"Negative\" 중 하나.")
        String polarity,

        @JsonPropertyDescription("판정 신뢰도. 0.0 ~ 1.0 사이의 실수.")
        double confidence,

        @JsonPropertyDescription("혼네/다테마에 분석 결과.")
        HonneDTO honne
) {}
