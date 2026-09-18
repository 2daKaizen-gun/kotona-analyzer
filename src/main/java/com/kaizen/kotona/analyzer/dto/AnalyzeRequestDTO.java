package com.kaizen.kotona.analyzer.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * /analyze 요청 바디.
 * text: 분석할 일본어 원문, relationshipType: 관계(기본 INTERNAL).
 */
public record AnalyzeRequestDTO(
        @NotBlank(message = "분석할 text는 필수입니다.")
        String text,
        RelationshipType relationshipType
) {
    /** 관계를 비워 두는 것은 허용한다 — 대부분의 요청이 사내다. 모르는 값은 그 전에 거절된다. */
    public RelationshipType relationshipTypeOrDefault() {
        return relationshipType == null ? RelationshipType.INTERNAL : relationshipType;
    }
}
