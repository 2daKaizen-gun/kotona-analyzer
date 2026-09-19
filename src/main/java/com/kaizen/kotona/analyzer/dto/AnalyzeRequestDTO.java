package com.kaizen.kotona.analyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * /analyze 요청 바디.
 * text: 분석할 일본어 원문, relationshipType: 관계(기본 INTERNAL).
 */
public record AnalyzeRequestDTO(
        @NotBlank(message = "분석할 text는 필수입니다.")
        @Size(max = AnalyzeRequestDTO.MAX_TEXT_LENGTH, message = "분석할 문장은 {max}자 이하여야 합니다.")
        String text,
        RelationshipType relationshipType
) {
    /**
     * 분석할 수 있는 최대 글자 수.
     *
     * <p>상한이 없던 때는 저장 단계에서야 걸렸다. user_input 은 TEXT(65,535 바이트)라
     * 일본어로 2만 자 남짓에서 INSERT 가 실패하는데, 그건 유료 모델 호출이 끝난 뒤다 —
     * 쿼터는 쓰고 결과는 버렸다. 여기서 막으면 호출 전에 끝난다.
     *
     * <p>2,000 자는 긴 업무 메일 한 통을 넉넉히 담는다. 이 서비스는 문장의 뉘앙스를 읽지
     * 문서를 요약하지 않는다. 글자 수는 {@code String.length()} 기준(UTF-16)이고,
     * 프론트의 {@code maxLength} 도 같은 기준으로 센다.
     */
    public static final int MAX_TEXT_LENGTH = 2000;

    /** 관계를 비워 두는 것은 허용한다 — 대부분의 요청이 사내다. 모르는 값은 그 전에 거절된다. */
    public RelationshipType relationshipTypeOrDefault() {
        return relationshipType == null ? RelationshipType.INTERNAL : relationshipType;
    }
}
