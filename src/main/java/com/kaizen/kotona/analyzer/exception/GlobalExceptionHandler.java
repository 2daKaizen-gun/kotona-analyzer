package com.kaizen.kotona.analyzer.exception;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;
import com.google.genai.errors.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Arrays;
import java.util.List;
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

    /** 우리가 거절한 입력(예: 분석 불가한 문장) → 400. 문구를 우리가 썼으므로 그대로 내보낸다. */
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<?> handleInvalidInput(InvalidInputException e) {
        return badRequest(messageOf(e));
    }

    /**
     * 라이브러리가 던진 인자 오류 → 400. 메시지는 로그에만 남긴다.
     *
     * <p>이 타입은 누구나 던진다. 메시지는 그 라이브러리가 자기 개발자에게 쓴 설명이라
     * 클래스·필드 이름이 섞여 있고, 사용자에게는 쓸모가 없다. 우리가 문구를 쓴 거절은
     * {@link InvalidInputException} 으로 따로 온다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("분류되지 않은 인자 오류", e);
        return badRequest("잘못된 요청입니다. 입력값을 확인하세요.");
    }

    /**
     * 본문을 읽을 수 없음 → 400. 깨진 JSON 이거나, enum 필드(situation 등)에 허용되지 않은 값이 온 경우다.
     * 아래 RuntimeException 핸들러로 떨어지면 500 이 되고 Jackson 내부 메시지가 그대로 샌다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleUnreadable(HttpMessageNotReadableException e) {
        if (e.getMostSpecificCause() instanceof InvalidFormatException ife
                && ife.getTargetType() != null && ife.getTargetType().isEnum()) {
            return badRequest(fieldName(ife.getPath()) + " 은(는) 다음 중 하나여야 합니다: "
                    + allowedValues(ife.getTargetType()));
        }
        return badRequest("요청 본문을 읽을 수 없습니다. JSON 형식을 확인하세요.");
    }

    /** 쿼리·경로 파라미터 타입 불일치 → 400 (예: /search?situation=EMIAL, /api/phrases/abc) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        Class<?> type = e.getRequiredType();
        if (type != null && type.isEnum()) {
            return badRequest(e.getName() + " 은(는) 다음 중 하나여야 합니다: " + allowedValues(type));
        }
        return badRequest(e.getName() + " 값의 형식이 올바르지 않습니다.");
    }

    /** 없는 숙어 id → 404 */
    @ExceptionHandler(HistoryNotFoundException.class)
    public ResponseEntity<?> handleHistoryNotFound(HistoryNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", messageOf(e)));
    }

    @ExceptionHandler(PhraseNotFoundException.class)
    public ResponseEntity<?> handleNotFound(PhraseNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", messageOf(e)));
    }

    /** 이미 등록된 표현 → 409 */
    @ExceptionHandler(DuplicatePhraseException.class)
    public ResponseEntity<?> handleDuplicate(DuplicatePhraseException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", messageOf(e)));
    }

    /**
     * DB 제약 위반 → 409.
     * 서비스가 중복을 먼저 확인하므로, 여기까지 오는 건 확인과 저장 사이에 같은 표현이 끼어든 경우다.
     * 원문에는 SQL 과 제약 이름이 담기므로 클라이언트에는 내보내지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 조건 위반", e);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "이미 등록된 표현이거나 저장할 수 없는 값입니다."));
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

    /** 분석 실패 → 500. 문구를 우리가 쓴 실패이므로 그대로 내보낸다. */
    @ExceptionHandler(AnalysisFailedException.class)
    public ResponseEntity<?> handleAnalysisFailed(AnalysisFailedException e) {
        log.error("분석 실패", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", messageOf(e)));
    }

    /**
     * 분류되지 않은 오류 → 500.
     *
     * <p>메시지는 내보내지 않고 로그에만 남긴다. 여기까지 온 예외는 우리가 문구를 써 둔 적이
     * 없는 예외이고, 그런 예외의 메시지는 내부 사정을 담는다 — JDBC 예외에는 실행하려던
     * 문장이, NullPointerException 에는 어느 클래스의 어느 필드가 비었는지가 들어 있다.
     * 위의 핸들러들이 공들여 감춘 것을 마지막에 되돌리지 않기 위함이다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        log.error("분류되지 않은 오류", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "서버에서 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
    }

    private ResponseEntity<?> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private String allowedValues(Class<?> enumType) {
        return Arrays.toString(enumType.getEnumConstants());
    }

    private String fieldName(List<JacksonException.Reference> path) {
        if (path.isEmpty() || path.get(path.size() - 1).getPropertyName() == null) {
            return "값";
        }
        return path.get(path.size() - 1).getPropertyName();
    }

    private String messageOf(Throwable e) {
        return Objects.requireNonNullElse(e.getMessage(), "알 수 없는 오류가 발생했습니다.");
    }
}
