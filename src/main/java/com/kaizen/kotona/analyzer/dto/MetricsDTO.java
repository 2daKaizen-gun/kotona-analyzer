package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record MetricsDTO (
    @JsonPropertyDescription("경어(敬語) 정확도 점수. 0~40 사이의 정수.")
    int politeness,

    @JsonPropertyDescription("간접 화법(曖昧語) 수준 점수. 0~30 사이의 정수.")
    int indirectness,

    @JsonPropertyDescription("쿠션어 등 비즈니스 에티켓 점수. 0~30 사이의 정수.")
    int etiquette
) {}
