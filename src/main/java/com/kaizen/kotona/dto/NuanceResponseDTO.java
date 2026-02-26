package com.kaizen.kotona.dto;

import java.util.List;

public record NuanceResponseDTO(
    int score,
    EvaluationDTO evaluation,
    FeedbackDTO feedback,
    List<SuggestionDTO> suggestions
) {}
