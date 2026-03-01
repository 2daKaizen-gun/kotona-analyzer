package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NuanceResponseDTO(
        @JsonProperty("totalScore")
        int totalScore, // 0 ~ 100

        MetricsDTO metrics,
        EvaluationDTO evaluation,
        FeedbackDTO feedback,
        List<SuggestionDTO> suggestions
) {}
