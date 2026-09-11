package com.kaizen.kotona.analyzer.entity;

/**
 * 숙어가 쓰이는 상황. 사전 검색(/api/phrases/search)의 필터 기준이다.
 *
 * <p>자유 문자열이던 시절에는 "EMIAL" 같은 오타가 그대로 저장되어 검색에서 조용히
 * 빠졌다. enum 으로 받으면 오타는 요청 단계에서 400 으로 거절된다.
 *
 * <p>DB 에는 이름 그대로(VARCHAR) 저장된다. 값을 추가하는 것은 자유롭지만,
 * 이름을 바꾸거나 지우면 기존 행을 읽을 수 없게 되므로 데이터 이전이 먼저다.
 */
public enum Situation {
    EMAIL,
    MEETING,
    INTERVIEW,
    NEGOTIATION,
    CONFIRMATION,
    REQUEST,
    NOTIFICATION,
    CUSHION
}
