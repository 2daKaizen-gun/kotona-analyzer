package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * 규칙이 모델 점수를 고친 한 건.
 *
 * <p>모델이 채우는 값이 아니라 서버가 채운다 — {@code NuanceSchemaFactory} 가
 * 모델에 보내는 스키마에서 이 필드를 빼는 이유다.
 *
 * <p>이 목록이 없던 동안, 사용자는 73점을 받고 그게 왜 73인지 알 수 없었다.
 * 모델이 83을 줬는데 규칙이 쿠션어가 없다며 10을 깎은 것인지, 모델이 처음부터
 * 73을 준 것인지 구분할 방법이 화면에도 API 에도 없었다.
 */
public record ScoreAdjustmentDTO(
        @JsonPropertyDescription("조정된 항목. \"politeness\", \"indirectness\", \"etiquette\", \"riskLevel\" 중 하나.")
        String metric,

        @JsonPropertyDescription("조정 전 값. 모델이 준 값이다.")
        String before,

        @JsonPropertyDescription("조정 후 값. 사용자가 보는 값이다.")
        String after,

        @JsonPropertyDescription("왜 조정했는지. 한국어로 작성한다.")
        String reason
) {
}
