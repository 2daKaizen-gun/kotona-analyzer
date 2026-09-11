package com.kaizen.kotona.analyzer.exception;

/** 이미 등록된 표현을 다시 등록하려 할 때 → 409 */
public class DuplicatePhraseException extends RuntimeException {

    public DuplicatePhraseException(String phrase) {
        super("이미 등록된 표현입니다: " + phrase);
    }
}
