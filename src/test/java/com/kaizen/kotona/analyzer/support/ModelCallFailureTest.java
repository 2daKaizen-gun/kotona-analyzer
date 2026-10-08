package com.kaizen.kotona.analyzer.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import com.kaizen.kotona.analyzer.support.ModelCallFailure.Kind;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델 호출 실패를 어떻게 읽는지 고정한다. 호출은 하지 않으므로 CI 에서 매번 돈다.
 *
 * <p>여기 쓰인 메시지는 전부 실제로 받은 것이다 — 지어낸 문자열로 맞추면 SDK 가 문구를
 * 바꿨을 때 이 테스트만 초록불로 남는다.
 */
class ModelCallFailureTest {

    /** 2026-10-06, 하루 한도를 다 쓴 뒤. */
    private static final String DAY_OVER = """
            429 RESOURCE_EXHAUSTED. You exceeded your current quota, please check your plan and billing details.
            * Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 20, model: gemini-3.6-flash
            Please retry in 23h39m36.206330045s.""";

    /** 2026-10-07, 분당 제한. 그날의 할당은 남아 있었다. */
    private static final String THROTTLED = """
            429 RESOURCE_EXHAUSTED. You exceeded your current quota, please check your plan and billing details.
            Please retry in 5.042516107s.""";

    /** 2026-10-08 의 실행에서 받은 503. 모델이 붐비는 것은 우리 잘못이 아니다. */
    private static final String OVERLOADED =
            "503 UNAVAILABLE. This model is currently experiencing high demand. "
                    + "Spikes in demand are usually temporary. Please try again later.";

    /** 키를 일부러 망가뜨려 확인한 응답. */
    private static final String BAD_KEY =
            "400 INVALID_ARGUMENT. API key not valid. Please pass a valid API key.";

    @Test
    @DisplayName("하루 한도는 쿼터로 읽고, 언제 열리는지까지 꺼낸다")
    void readsTheDailyLimit() {
        ModelCallFailure failure = ModelCallFailure.of(new RuntimeException(DAY_OVER));

        assertThat(failure.kind()).isEqualTo(Kind.DAILY_LIMIT);
        assertThat(failure.ours()).isFalse();
        assertThat(failure.dayIsOver()).isTrue();
        // 기다려도 오늘은 소용없다 — 다음 실행이 이어 간다.
        assertThat(failure.waitBeforeRetry()).isNull();
        // 23h39m36.2s — 시·분·초가 모두 있는 형태
        assertThat(failure.retryAfter()).isBetween(Duration.ofHours(23), Duration.ofHours(24));
        assertThat(failure.summary()).contains("쿼터 소진", "23h39m");
    }

    @Test
    @DisplayName("분당 제한은 몇 초로 읽혀, 기다릴 수 있는 길이가 된다")
    void readsTheMinuteThrottle() {
        ModelCallFailure failure = ModelCallFailure.of(new RuntimeException(THROTTLED));

        // 같은 429 이지만 기다리면 이어서 물을 수 있다. 이 둘을 가리지 못하면
        // 몇 초짜리 제한 때문에 그날 남은 할당을 버리게 된다.
        assertThat(failure.kind()).isEqualTo(Kind.THROTTLE);
        assertThat(failure.dayIsOver()).isFalse();
        assertThat(failure.waitBeforeRetry()).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("쿼터가 아닌 실패는 우리 쪽 문제로 읽고, 메시지를 그대로 보여 준다")
    void readsOurOwnBreakage() {
        ModelCallFailure failure = ModelCallFailure.of(new IllegalStateException(BAD_KEY));

        assertThat(failure.kind()).isEqualTo(Kind.OURS);
        assertThat(failure.ours()).isTrue();
        assertThat(failure.retryAfter()).isNull();
        assertThat(failure.waitBeforeRetry()).isNull();
        assertThat(failure.summary())
                .contains("IllegalStateException")
                .contains("API key not valid");
    }

    @Test
    @DisplayName("메시지가 없는 예외도 한 줄로 읽힌다")
    void survivesAMessagelessException() {
        ModelCallFailure failure = ModelCallFailure.of(new RuntimeException());

        assertThat(failure.ours()).isTrue();
        assertThat(failure.summary()).contains("RuntimeException");
    }

    @Test
    @DisplayName("모델 과부하는 우리 쪽 문제가 아니라, 잠깐 기다릴 일이다")
    void readsAnOverloadedModel() {
        // 이 503 때문에 2026-10-08 의 측정이 빨간불로 끝났다. 429 만 사정으로 보고 있었던 탓이다.
        ModelCallFailure failure = ModelCallFailure.of(new RuntimeException(OVERLOADED));

        assertThat(failure.kind()).isEqualTo(Kind.UPSTREAM);
        assertThat(failure.ours()).isFalse();
        assertThat(failure.dayIsOver()).isFalse();
        assertThat(failure.waitBeforeRetry()).isNotNull().isLessThan(Duration.ofSeconds(30));
        assertThat(failure.summary()).contains("과부하");
    }

    @Test
    @DisplayName("예외 클래스 이름만으로도 서버 쪽 실패를 알아본다")
    void readsAServerExceptionByItsName() {
        // SDK 가 문구를 바꿔도 ServerException 은 5xx 라는 뜻이다.
        class ServerException extends RuntimeException {
            ServerException() {
                super("something upstream went wrong");
            }
        }

        assertThat(ModelCallFailure.of(new ServerException()).kind()).isEqualTo(Kind.UPSTREAM);
    }
}
