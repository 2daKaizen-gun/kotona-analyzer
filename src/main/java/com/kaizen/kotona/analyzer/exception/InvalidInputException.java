package com.kaizen.kotona.analyzer.exception;

/**
 * 우리가 입력을 보고 거절했을 때. 메시지는 사용자에게 보일 것을 전제로 쓴다.
 *
 * <p>예전에는 {@link IllegalArgumentException} 을 그대로 썼다. 그런데 그 타입은 라이브러리도
 * 던진다 — 핸들러는 우리가 쓴 "올바른 일본어를 입력하세요" 와 라이브러리 내부의 인자 설명을
 * 구분할 수 없었고, 둘 다 400 본문에 실었다. {@link AnalysisFailedException} 이 500 쪽에서
 * 하는 일을 400 쪽에서 한다.
 */
public class InvalidInputException extends RuntimeException {

    public InvalidInputException(String message) {
        super(message);
    }
}
