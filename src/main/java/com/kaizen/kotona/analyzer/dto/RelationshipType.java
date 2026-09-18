package com.kaizen.kotona.analyzer.dto;

/**
 * 문장을 주고받는 상대와의 관계. 같은 문장도 상대에 따라 위험도가 달라진다.
 *
 * <p>자유 문자열이던 시절에는 두 가지가 조용히 일어났다. 모르는 값은 사내(1.0)로 계산되어
 * 오타 하나가 "안전합니다" 라는 확신에 찬 점수를 돌려줬고, 그 문자열이 그대로 모델 프롬프트의
 * 관계 항목에 실렸다 — 호출자가 프롬프트에 원하는 문장을 쓸 수 있었다는 뜻이다.
 * enum 으로 받으면 둘 다 요청 단계에서 400 으로 끝난다.
 *
 * <p>배수를 여기 두는 이유도 같다. {@code switch} 의 {@code default} 는 값이 늘어날 때
 * 아무 말 없이 1.0 을 준다. 상수에 붙여 두면 값을 추가하면서 배수를 빠뜨릴 수 없다.
 */
public enum RelationshipType {

    /** 사내. 기준값이다. */
    INTERNAL(1.0),

    /** 사외·고객사. 같은 결례라도 파장이 크다. */
    EXTERNAL(1.2),

    /** 면접. 한 번의 실수가 결과를 바꾼다. */
    INTERVIEW(1.5);

    private final double riskMultiplier;

    RelationshipType(double riskMultiplier) {
        this.riskMultiplier = riskMultiplier;
    }

    /** 규칙 기반 리스크 점수에 곱하는 맥락 가중치. */
    public double riskMultiplier() {
        return riskMultiplier;
    }
}
