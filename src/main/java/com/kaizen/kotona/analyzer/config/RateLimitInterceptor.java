package com.kaizen.kotona.analyzer.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IP별 고정 윈도우 요청 제한기.
 * 공개된 유료 /analyze 엔드포인트의 남용/비용 폭탄을 막는다.
 * (분산 환경에서는 Redis 기반으로 교체 필요 — 단일 인스턴스/로컬 기준 구현)
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final int MAX_REQUESTS = 20;      // 윈도우당 최대 요청
    private static final long WINDOW_MS = 60_000;    // 1분

    private final Map<String, Window> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        long now = System.currentTimeMillis();
        Window window = buckets.compute(clientIp(request), (ip, current) -> {
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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
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
