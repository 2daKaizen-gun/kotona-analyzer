package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record FeedbackDTO (
        List<String> issues,

        @JsonProperty("cultural_nuance")
        String culturalNuance
) {}
