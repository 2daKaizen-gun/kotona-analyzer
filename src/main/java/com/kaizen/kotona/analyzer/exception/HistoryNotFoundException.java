package com.kaizen.kotona.analyzer.exception;

/** 존재하지 않는 분석 이력 id → 404 */
public class HistoryNotFoundException extends RuntimeException {

    public HistoryNotFoundException(Long id) {
        super("id=" + id + " 인 분석 이력을 찾을 수 없습니다.");
    }
}
