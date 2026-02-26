package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EvaluationDTO(
        String summary,

        @JsonProperty("keigo_check")
        boolean keigoCheck,

        @JsonProperty("cushion_phrase_check")
        boolean cushionPhraseCheck
) {}
