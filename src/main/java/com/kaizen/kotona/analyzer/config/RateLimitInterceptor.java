package com.kaizen.kotona.analyzer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IP별 고정 윈도우 요청 제한기.
 * 공개된 유료 /analyze 엔드포인트의 남용/비용 폭탄을 막는다.
 * (분산 환경에서는 Redis 기반으로 교체 필요 — 단일 인스턴스/로컬 기준 구현)
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    static final int MAX_REQUESTS = 20;      // 윈도우당 최대 요청
    static final long WINDOW_MS = 60_000;    // 1분

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    /**
     * X-Forwarded-For 신뢰 여부. 리버스 프록시 뒤에 있을 때만 true 로 둔다.
     * 직접 노출된 서버에서 이걸 신뢰하면 공격자가 헤더만 바꿔가며 제한을 무한 우회한다.
     */
    private final boolean trustForwardedFor;

    /** 테스트가 시간을 앞으로 돌릴 수 있도록 주입받는다. */
    private final Clock clock;

    /** 마지막으로 만료 버킷을 쓸어낸 시각. */
    private final AtomicLong lastSweep;

    public RateLimitInterceptor(boolean trustForwardedFor) {
        this(trustForwardedFor, Clock.systemUTC());
    }

    RateLimitInterceptor(boolean trustForwardedFor, Clock clock) {
        this.trustForwardedFor = trustForwardedFor;
        this.clock = clock;
        this.lastSweep = new AtomicLong(clock.millis());
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        long now = clock.millis();
        sweepIfDue(now);

        Window window = buckets.compute(clientIp(request), (ip, current) -> {
            // 이 IP 의 윈도우가 끝났으면 새로 연다. 청소가 아직 안 돌았어도
            // 여기서 판단하므로 제한 자체는 정확하다.
            if (current == null || now - current.windowStart >= WINDOW_MS) {
                return new Window(now);
            }
            current.count++;
            return current;
        });

        if (window.count > MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Too Many Requests\",\"message\":\"요청이 너무 많습니다. 잠시 후 다시 시도하세요.\"}");
            return false;
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    /**
     * 만료된 버킷을 쓸어낸다. 없으면 접속한 IP 수만큼 맵이 무한히 커진다.
     *
     * <p>윈도우마다 한 번만 돈다. 매 요청 전체를 훑으면 IP 가 늘어날수록
     * 요청당 비용이 같이 늘어나는데, 제한 판정은 어차피 compute 안에서
     * 하므로 청소를 미뤄도 정확도에는 영향이 없다.
     */
    private void sweepIfDue(long now) {
        long last = lastSweep.get();
        if (now - last < WINDOW_MS) {
            return;
        }
        // 경쟁에서 진 스레드는 그냥 넘어간다. 다음 요청이 다시 시도한다.
        if (!lastSweep.compareAndSet(last, now)) {
            return;
        }
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStart >= WINDOW_MS);
    }

    /** 테스트에서 맵이 실제로 줄어드는지 보기 위한 창구. */
    int trackedClients() {
        return buckets.size();
    }

    private static final class Window {
        final long windowStart;
        int count;

        Window(long windowStart) {
            this.windowStart = windowStart;
            this.count = 1;
        }
    }
}
