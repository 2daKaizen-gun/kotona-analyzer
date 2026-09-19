package com.kaizen.kotona.analyzer.dto;

import com.kaizen.kotona.analyzer.entity.Situation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 숙어 등록(POST)·수정(PUT) 요청 바디. PUT 은 전체 교체이므로 두 요청이 같은 모양이다.
 *
 * <p>길이 제한은 컬럼 크기(VARCHAR 255)에 맞춘다. 검증 없이 넘기면 DB 가 "Data too long" 으로
 * 거절하는데, 그 예외는 중복 위반과 같은 DataIntegrityViolationException 이라 409 로 잘못 안내된다.
 *
 * <p>예문(usage_example)은 TEXT 라 이 규칙에서 한동안 빠져 있었다. 65,535 바이트를 넘기면
 * 똑같이 409 "이미 등록된 표현이거나…" 가 나갔다 — 아직 등록되지도 않은 표현에 대해.
 * 한도는 컬럼이 아니라 쓰임새로 정한다. 예문은 한두 문장이다.
 */
public record PhraseRequestDTO(
        @NotBlank(message = "표현(phrase)은 필수입니다.")
        @Size(max = 255, message = "표현(phrase)은 255자 이하여야 합니다.")
        String phrase,

        @NotBlank(message = "뜻(meaning)은 필수입니다.")
        @Size(max = 255, message = "뜻(meaning)은 255자 이하여야 합니다.")
        String meaning,

        Situation situation,

        @Min(value = 1, message = "정중도(politenessLevel)는 1~5 사이여야 합니다.")
        @Max(value = 5, message = "정중도(politenessLevel)는 1~5 사이여야 합니다.")
        Integer politenessLevel,

        @Size(max = 500, message = "예문(usageExample)은 500자 이하여야 합니다.")
        String usageExample
) {
}
