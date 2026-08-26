package com.kaizen.kotona.analyzer.exception;

import com.google.genai.errors.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.Objects;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 요청 바디 검증 실패 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("잘못된 요청입니다.");
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /** 잘못된 입력(예: 분석 불가한 문장) → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", messageOf(e)));
    }

    /**
     * Gemini API 오류 매핑. 429(쿼터/레이트리밋)는 그대로 429 로,
     * 나머지는 502 로 내보낸다. 업스트림 원문에는 내부 메시지가 담기므로
     * 클라이언트에 그대로 전달하지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> handleGeminiError(ApiException e) {
        log.error("Gemini API 호출 실패 (code={})", e.code(), e);

        if (e.code() == 429) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "AI API 호출 한도를 초과했습니다. 잠시 후 다시 시도하세요."));
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "AI 분석 서비스에 일시적인 문제가 발생했습니다. 서버 로그를 확인하세요."));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", messageOf(e)));
    }

    private String messageOf(Throwable e) {
        return Objects.requireNonNullElse(e.getMessage(), "알 수 없는 오류가 발생했습니다.");
    }
}
