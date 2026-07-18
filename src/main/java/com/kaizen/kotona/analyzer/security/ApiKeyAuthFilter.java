package com.kaizen.kotona.analyzer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 유료 AI 엔드포인트(/analyze)를 X-API-KEY 헤더로 보호한다.
 * - API_KEY 환경변수가 설정된 경우에만 강제(설정 안 하면 경고 후 통과 → 로컬 개발 편의).
 * - health check / swagger 등 나머지 경로는 열어둔다.
 */
@Slf4j
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-KEY";
    private static final String PROTECTED_PREFIX = "/analyze";

    @Value("${API_KEY:}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (request.getRequestURI().startsWith(PROTECTED_PREFIX)) {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("API_KEY 미설정: /analyze 인증이 비활성화되어 있습니다. 운영 환경에서는 반드시 설정하세요.");
            } else {
                String provided = request.getHeader(HEADER);
                if (!matches(provided, apiKey)) {
                    writeUnauthorized(response);
                    return;
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 타이밍 공격 방지를 위한 상수 시간 비교 */
    private boolean matches(String provided, String expected) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"error\":\"Unauthorized\",\"message\":\"유효한 X-API-KEY 헤더가 필요합니다.\"}");
    }
}
