package com.kaizen.kotona.analyzer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /analyze 요청 바디.
 * text: 분석할 일본어 원문, relationshipType: 관계(기본 INTERNAL).
 */
public record AnalyzeRequestDTO(
        @NotBlank(message = "분석할 text는 필수입니다.")
        String text,
        String relationshipType
) {
    public String relationshipTypeOrDefault() {
        return (relationshipType == null || relationshipType.isBlank())
                ? "INTERNAL"
                : relationshipType;
    }
}
