package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SuggestionDTO (
        @JsonPropertyDescription("개선된 대안 문장. 반드시 일본어로 작성한다.")
        String text,

        @JsonPropertyDescription("정중도 수준. \"standard\" 또는 \"highest\" 중 하나.")
        String level
) {}
