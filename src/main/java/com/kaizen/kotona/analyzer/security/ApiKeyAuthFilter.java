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
import java.util.List;
import java.util.Set;

/**
 * 민감한 엔드포인트를 X-API-KEY 헤더로 보호한다.
 * - API_KEY 환경변수가 설정된 경우에만 강제(설정 안 하면 경고 후 통과 → 로컬 개발 편의).
 * - 보호 대상: /analyze(쿼터 소모), /api/history(입력 원문 노출), /api/phrases 의 쓰기 요청.
 * - health check / swagger / 사전 조회 등 나머지는 열어둔다.
 */
@Slf4j
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-KEY";

    /**
     * /analyze 는 유료/쿼터 소모, /api/history 는 사용자가 입력한 원문이 그대로 담긴다.
     * 둘 다 인증 없이 열어둘 대상이 아니다.
     */
    private static final List<String> PROTECTED_PREFIXES = List.of("/analyze", "/api/history");

    /**
     * 읽기는 열어 두고 쓰기만 막는 경로. 사전 조회는 누구나 해도 되지만,
     * 추가·수정·삭제를 열어 두면 공유 사전을 아무나 고치거나 지울 수 있다.
     */
    private static final List<String> WRITE_PROTECTED_PREFIXES = List.of("/api/phrases");

    /** 상태를 바꾸지 않는 메서드. OPTIONS 는 CORS preflight 라 헤더 없이 온다. */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Value("${API_KEY:}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isProtected(request)) {
            if (apiKey == null || apiKey.isBlank()) {
                log.warn("API_KEY 미설정: {} {} 인증이 비활성화되어 있습니다. 운영 환경에서는 반드시 설정하세요.",
                        request.getMethod(), request.getRequestURI());
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

    private boolean isProtected(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (PROTECTED_PREFIXES.stream().anyMatch(uri::startsWith)) {
            return true;
        }
        return !READ_METHODS.contains(request.getMethod())
                && WRITE_PROTECTED_PREFIXES.stream().anyMatch(uri::startsWith);
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
