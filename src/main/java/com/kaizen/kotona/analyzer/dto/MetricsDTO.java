package com.kaizen.kotona.analyzer.dto;

public record MetricsDTO (
    int politeness, // 경어 사용(0~40)
    int indirectness, // 간접 화법(0~40)
    int etiquette // 쿠션어, 에티켓(0~30)
) {}
