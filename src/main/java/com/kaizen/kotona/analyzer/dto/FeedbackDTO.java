package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record FeedbackDTO (
        @JsonPropertyDescription("문법적·문화적으로 지적할 문제점 목록. 한국어로 작성한다.")
        List<String> issues,

        @JsonProperty("cultural_nuance")
        @JsonPropertyDescription("일본 비즈니스 문화 관점의 뉘앙스 해설. 한국어로 작성한다.")
        String culturalNuance
) {}
