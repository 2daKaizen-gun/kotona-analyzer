package com.kaizen.kotona.analyzer.dto;

public record SentimentDTO(
        String polarity, // Positive, Neutral, Negative
        double confidence, // 0.0 ~ 1.0
        HonneDTO honne // 속마음 분석 결과
) {}
