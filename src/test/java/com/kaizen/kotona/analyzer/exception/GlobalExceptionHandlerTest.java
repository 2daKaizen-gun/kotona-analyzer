package com.kaizen.kotona.analyzer.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import com.kaizen.kotona.analyzer.entity.Situation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이 클래스가 모든 실패의 얼굴이다. 어떤 예외가 어떤 상태코드로 나가는지는
 * 클라이언트와의 계약이고, 프론트는 그 코드에 따라 다르게 행동한다.
 *
 * <p>상태코드만큼 중요한 것이 "무엇을 내보내지 않는가" 다. DB 제약 위반에는 SQL 과
 * 제약 이름이, 모델 오류에는 업스트림 응답이 담겨 있다. 둘 다 클라이언트에 도달하면 안 된다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Nested
    @DisplayName("잘못된 요청 → 400")
    class BadRequest {

        @Test
        @DisplayName("본문 검증이 실패하면 첫 번째 필드 메시지를 그대로 보여 준다")
        void showsTheFirstFieldMessage() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
            binding.addError(new FieldError("request", "phrase", "표현(phrase)은 필수입니다."));

            ResponseEntity<?> response = handler.handleValidation(
                    new MethodArgumentNotValidException(anyMethodParameter(), binding));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorOf(response)).isEqualTo("표현(phrase)은 필수입니다.");
        }

        @Test
        @DisplayName("필드 오류가 없으면 일반 문구로 답한다")
        void fallsBackWhenNoFieldErrorIsPresent() {
            BindingResult binding = new BeanPropertyBindingResult(new Object(), "request");

            ResponseEntity<?> response = handler.handleValidation(
                    new MethodArgumentNotValidException(anyMethodParameter(), binding));

            assertThat(errorOf(response)).isEqualTo("잘못된 요청입니다.");
        }

        @Test
        @DisplayName("분석할 수 없는 입력은 400 으로 내려간다")
        void mapsIllegalArgumentToBadRequest() {
            ResponseEntity<?> response = handler.handleIllegalArgument(
                    new IllegalArgumentException("분석할 수 없는 문장입니다."));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorOf(response)).isEqualTo("분석할 수 없는 문장입니다.");
        }

        @Test
        @DisplayName("깨진 JSON 은 형식을 확인하라고 안내한다")
        void explainsMalformedJson() {
            ResponseEntity<?> response = handler.handleUnreadable(
                    new HttpMessageNotReadableException("broken", emptyInput()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorOf(response)).contains("JSON 형식을 확인하세요");
        }

        @Test
        @DisplayName("enum 필드에 없는 값이 오면 허용값을 알려 준다")
        void listsAllowedValuesForAnUnknownEnum() {
            // 이게 없으면 Jackson 내부 메시지가 그대로 500 으로 새어 나간다
            InvalidFormatException cause =
                    InvalidFormatException.from(null, "no enum", "EMIAL", Situation.class);
            cause.prependPath(new JsonMappingException.Reference(Object.class, "situation"));

            ResponseEntity<?> response = handler.handleUnreadable(
                    new HttpMessageNotReadableException("bad enum", cause, emptyInput()));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorOf(response))
                    .startsWith("situation")
                    .contains("EMAIL")
                    .contains("CUSHION");
        }

        @Test
        @DisplayName("쿼리 파라미터의 enum 값이 틀려도 허용값을 알려 준다")
        void listsAllowedValuesForAQueryParameter() {
            ResponseEntity<?> response = handler.handleTypeMismatch(
                    new MethodArgumentTypeMismatchException(
                            "EMIAL", Situation.class, "situation", anyMethodParameter(), null));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(errorOf(response)).startsWith("situation").contains("MEETING");
        }

        @Test
        @DisplayName("enum 이 아닌 파라미터는 형식이 틀렸다고만 알린다")
        void reportsANonEnumMismatchPlainly() {
            // 예: /api/phrases/abc
            ResponseEntity<?> response = handler.handleTypeMismatch(
                    new MethodArgumentTypeMismatchException(
                            "abc", Long.class, "id", anyMethodParameter(), null));

            assertThat(errorOf(response)).isEqualTo("id 값의 형식이 올바르지 않습니다.");
        }
    }

    @Nested
    @DisplayName("없는 자원 → 404, 중복 → 409")
    class NotFoundAndConflict {

        @Test
        @DisplayName("없는 이력과 없는 표현은 모두 404 다")
        void mapsMissingResourcesToNotFound() {
            assertThat(handler.handleHistoryNotFound(new HistoryNotFoundException(7L)).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(handler.handleNotFound(new PhraseNotFoundException(7L)).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("이미 있는 표현은 409 다")
        void mapsDuplicateToConflict() {
            ResponseEntity<?> response =
                    handler.handleDuplicate(new DuplicatePhraseException("承知いたしました"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(errorOf(response)).contains("承知いたしました");
        }

        @Test
        @DisplayName("DB 제약 위반은 409 로 바꾸되 SQL 은 내보내지 않는다")
        void neverLeaksTheConstraintViolation() {
            // 원문에는 테이블·제약 이름과 SQL 이 담긴다. 그대로 나가면 스키마를 알려 주는 셈이다.
            ResponseEntity<?> response = handler.handleDataIntegrity(
                    new DataIntegrityViolationException(
                            "Duplicate entry '承知' for key 'business_phrase.uk_business_phrase_phrase'"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(errorOf(response))
                    .doesNotContain("uk_business_phrase_phrase")
                    .doesNotContain("Duplicate entry");
        }
    }

    @Nested
    @DisplayName("모델 오류")
    class UpstreamFailures {

        @Test
        @DisplayName("쿼터 초과는 429 로 그대로 전한다")
        void passesRateLimitingThrough() {
            // 프론트가 이 코드를 보고 "잠시 후 다시" 를 안내한다
            ResponseEntity<?> response = handler.handleGeminiError(
                    new ClientException(429, "RESOURCE_EXHAUSTED", "quota exceeded"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
            assertThat(errorOf(response)).contains("한도");
        }

        @Test
        @DisplayName("그 밖의 모델 오류는 502 이고 업스트림 응답은 새지 않는다")
        void neverLeaksTheUpstreamBody() {
            // 실제로 관측된 응답에는 request_id 와 계정 상태가 담겨 있었다
            ResponseEntity<?> response = handler.handleGeminiError(new ClientException(
                    400, "INVALID_ARGUMENT",
                    "This model models/gemini-2.5-flash is no longer available to new users. request_id=abc123"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
            assertThat(errorOf(response))
                    .doesNotContain("request_id")
                    .doesNotContain("gemini-2.5-flash");
        }

        @Test
        @DisplayName("모델 쪽 5xx 도 502 로 모은다")
        void mapsUpstreamServerErrorsToBadGateway() {
            assertThat(handler.handleGeminiError(new ServerException(503, "UNAVAILABLE", "overloaded"))
                    .getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        }
    }

    @Nested
    @DisplayName("그 외")
    class Fallback {

        @Test
        @DisplayName("분류되지 않은 오류는 500 이다")
        void mapsUnclassifiedErrorsToServerError() {
            ResponseEntity<?> response = handler.handleRuntimeException(new RuntimeException("boom"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(errorOf(response)).isNotBlank();
        }

        @Test
        @DisplayName("분류되지 않은 오류의 메시지는 내보내지 않는다")
        void hidesTheMessageOfAnUnclassifiedError() {
            // 여기까지 오는 예외는 우리가 문구를 써 둔 적이 없는 예외다. 그 메시지는 내부 사정을
            // 담는다 — 아래는 JDBC 드라이버와 JDK 가 실제로 만드는 모양이다. 앞선 핸들러들이
            // DB·모델 메시지를 감추는 것과 같은 이유로 이것도 나가면 안 된다.
            String[] internal = {
                    "could not execute statement [Duplicate entry 'x' for key 'business_phrase.UK_phrase']",
                    "Cannot invoke \"com.kaizen.kotona.analyzer.entity.BusinessPhrase.getPhrase()\" "
                            + "because \"phrase\" is null",
                    "Access denied for user 'kotona'@'localhost'",
            };

            for (String message : internal) {
                String body = errorOf(handler.handleRuntimeException(new RuntimeException(message)));

                assertThat(body).doesNotContain("Duplicate entry", "business_phrase", "getPhrase",
                        "Access denied", "kotona'@'localhost");
            }
        }

        @Test
        @DisplayName("메시지가 없는 예외에도 빈 응답을 주지 않는다")
        void neverRespondsWithANullMessage() {
            // getMessage() 가 null 인 예외는 흔하다. 그대로 두면 본문이 {"error": null} 이 된다.
            ResponseEntity<?> response = handler.handleRuntimeException(new RuntimeException());

            assertThat(errorOf(response)).isNotBlank().isNotEqualTo("null");
        }
    }

    // --- 헬퍼 ---

    private String errorOf(ResponseEntity<?> response) {
        assertThat(response.getBody()).isInstanceOf(Map.class);
        return String.valueOf(((Map<?, ?>) response.getBody()).get("error"));
    }

    private MockHttpInputMessage emptyInput() {
        return new MockHttpInputMessage(new byte[0]);
    }

    /** 예외 생성에만 쓰이는 자리 채우기. 어떤 메서드를 가리키는지는 핸들러가 보지 않는다. */
    private MethodParameter anyMethodParameter() {
        try {
            return new MethodParameter(
                    GlobalExceptionHandlerTest.class.getDeclaredMethod("placeholder", String.class), 0);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private void placeholder(String value) {
    }
}
