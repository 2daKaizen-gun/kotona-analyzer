package com.kaizen.kotona.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record NuanceResponseDTO(
        @JsonProperty("totalScore")
        // 단일 책임 원칙, 확장성
        int totalScore, // 0 ~ 100
        MetricsDTO metrics,
        EvaluationDTO evaluation,
        FeedbackDTO feedback,
        List<SuggestionDTO> suggestions
) {}
