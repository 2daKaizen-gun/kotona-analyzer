package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record EvaluationDTO(
        @JsonPropertyDescription("분석 총평. 반드시 한국어로 작성한다.")
        String summary,

        @JsonProperty("keigo_check")
        @JsonPropertyDescription("경어가 문법적으로 올바르게 쓰였는지 여부.")
        boolean keigoCheck,

        @JsonProperty("cushion_phrase_check")
        @JsonPropertyDescription("쿠션어(お手数ですが 등)가 사용되었는지 여부.")
        boolean cushionPhraseCheck
) {}
