package com.kaizen.kotona.analyzer.exception;

/** 존재하지 않는 숙어 id → 404 */
public class PhraseNotFoundException extends RuntimeException {

    public PhraseNotFoundException(Long id) {
        super("id=" + id + " 인 표현을 찾을 수 없습니다.");
    }
}
