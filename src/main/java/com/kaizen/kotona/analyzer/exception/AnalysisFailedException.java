package com.kaizen.kotona.analyzer.exception;

/**
 * 분석이 우리가 예상한 방식으로 실패했을 때. 메시지는 사용자에게 보일 것을 전제로 쓴다.
 *
 * <p>이 타입이 따로 있는 이유는, 분류되지 않은 오류의 메시지가 더 이상 응답에 실리지
 * 않기 때문이다. 내보낼 문구를 가진 실패는 그렇다고 말해야 한다 — 그래야 원인 쪽 메시지
 * (JDBC 문장, 벤더 응답)와 우리가 쓴 문구를 핸들러가 구분할 수 있다.
 */
public class AnalysisFailedException extends RuntimeException {

    public AnalysisFailedException(String message) {
        super(message);
    }

    public AnalysisFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
