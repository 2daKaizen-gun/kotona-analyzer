package com.kaizen.kotona.analyzer.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.kaizen.kotona.analyzer.config.RateLimitInterceptor.MAX_REQUESTS;
import static com.kaizen.kotona.analyzer.config.RateLimitInterceptor.WINDOW_MS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * /analyze 는 호출마다 모델 쿼터를 쓴다. 이 제한기가 뚫리면 비용으로 직결되므로
 * 한도·윈도우 갱신·헤더 신뢰 여부를 모두 고정해 둔다.
 */
class RateLimitInterceptorTest {

    /** 테스트가 시간을 직접 돌린다. 실제 시계에 의존하면 윈도우 갱신을 볼 수 없다. */
    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advanceMillis(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private final MovableClock clock = new MovableClock();

    @Nested
    @DisplayName("한도")
    class Limit {

        private final RateLimitInterceptor interceptor = new RateLimitInterceptor(false, clock);

        @Test
        @DisplayName("한도까지는 통과시키고 그 다음 요청을 막는다")
        void allowsUpToTheLimitThenBlocks() throws Exception {
            for (int i = 1; i <= MAX_REQUESTS; i++) {
                assertThat(call(interceptor, "10.0.0.1").allowed())
                        .as("%d번째 요청", i)
                        .isTrue();
            }

            assertThat(call(interceptor, "10.0.0.1").allowed()).isFalse();
        }

        @Test
        @DisplayName("막을 때 429 와 안내 문구를 돌려준다")
        void respondsWith429AndAMessage() throws Exception {
            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1");
            }

            Result blocked = call(interceptor, "10.0.0.1");

            assertThat(blocked.allowed()).isFalse();
            assertThat(blocked.response().getStatus()).isEqualTo(429);
            assertThat(blocked.response().getContentType()).contains("application/json");
            assertThat(blocked.response().getContentAsString()).contains("요청이 너무 많습니다");
        }

        @Test
        @DisplayName("IP 마다 따로 센다")
        void countsEachClientSeparately() throws Exception {
            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1");
            }
            assertThat(call(interceptor, "10.0.0.1").allowed()).isFalse();

            assertThat(call(interceptor, "10.0.0.2").allowed()).isTrue();
        }

        @Test
        @DisplayName("윈도우가 지나면 다시 통과한다")
        void reopensAfterTheWindowPasses() throws Exception {
            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1");
            }
            assertThat(call(interceptor, "10.0.0.1").allowed()).isFalse();

            clock.advanceMillis(WINDOW_MS);

            assertThat(call(interceptor, "10.0.0.1").allowed()).isTrue();
        }

        @Test
        @DisplayName("윈도우가 끝나기 전에는 계속 막는다")
        void keepsBlockingWithinTheWindow() throws Exception {
            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1");
            }

            clock.advanceMillis(WINDOW_MS - 1);

            assertThat(call(interceptor, "10.0.0.1").allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("클라이언트 식별")
    class ClientIdentity {

        @Test
        @DisplayName("프록시를 신뢰하지 않으면 X-Forwarded-For 를 무시한다")
        void ignoresForwardedForWhenProxyIsNotTrusted() throws Exception {
            // 이걸 무조건 믿으면 공격자가 헤더만 바꿔 가며 제한을 무한 우회한다
            RateLimitInterceptor interceptor = new RateLimitInterceptor(false, clock);

            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1", "1.1.1." + i);
            }

            assertThat(call(interceptor, "10.0.0.1", "9.9.9.9").allowed()).isFalse();
        }

        @Test
        @DisplayName("프록시를 신뢰하면 X-Forwarded-For 의 첫 주소로 센다")
        void usesTheFirstForwardedAddressWhenProxyIsTrusted() throws Exception {
            RateLimitInterceptor interceptor = new RateLimitInterceptor(true, clock);

            // 같은 원 IP 가 프록시 체인을 거쳐 들어온 모양
            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1", "203.0.113.7, 10.0.0.5");
            }
            assertThat(call(interceptor, "10.0.0.1", "203.0.113.7, 10.0.0.5").allowed()).isFalse();

            // 다른 원 IP 는 영향받지 않는다
            assertThat(call(interceptor, "10.0.0.1", "203.0.113.8").allowed()).isTrue();
        }

        @Test
        @DisplayName("프록시를 신뢰해도 헤더가 없으면 소켓 주소로 센다")
        void fallsBackToTheSocketAddress() throws Exception {
            RateLimitInterceptor interceptor = new RateLimitInterceptor(true, clock);

            for (int i = 0; i <= MAX_REQUESTS; i++) {
                call(interceptor, "10.0.0.1");
            }

            assertThat(call(interceptor, "10.0.0.1").allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("메모리")
    class Memory {

        @Test
        @DisplayName("윈도우가 지나면 쓰지 않는 버킷을 버린다")
        void dropsStaleBuckets() throws Exception {
            RateLimitInterceptor interceptor = new RateLimitInterceptor(false, clock);

            for (int i = 0; i < 50; i++) {
                call(interceptor, "10.0.0." + i);
            }
            assertThat(interceptor.trackedClients()).isEqualTo(50);

            // 청소는 윈도우마다 한 번 돈다. 시간을 넘기고 아무 요청이나 한 번 더 보낸다.
            clock.advanceMillis(WINDOW_MS + 1);
            call(interceptor, "10.0.0.99");

            assertThat(interceptor.trackedClients()).isEqualTo(1);
        }
    }

    // --- 헬퍼 ---

    private record Result(boolean allowed, MockHttpServletResponse response) {
    }

    private Result call(RateLimitInterceptor interceptor, String remoteAddr) throws Exception {
        return call(interceptor, remoteAddr, null);
    }

    private Result call(RateLimitInterceptor interceptor, String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyze");
        request.setRemoteAddr(remoteAddr);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(request, response, new Object());
        return new Result(allowed, response);
    }
}
