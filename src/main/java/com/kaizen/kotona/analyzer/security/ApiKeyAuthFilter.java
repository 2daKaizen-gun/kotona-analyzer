package com.kaizen.kotona.analyzer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

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

    /**
     * 키가 맞은 요청에 붙이는 표시. 키를 가진 호출자는 우리 프론트(kotona-web 의 route handler)뿐이다.
     *
     * <p>{@code RateLimitInterceptor} 가 이 표시를 보고 그 호출자가 전달한 사용자 IP 를 믿는다.
     * 표시가 없으면 믿을 근거가 없다 — 아무나 헤더를 바꿔 가며 제한을 우회할 수 있다.
     */
    public static final String VERIFIED_CALLER = ApiKeyAuthFilter.class.getName() + ".VERIFIED_CALLER";

    @Value("${API_KEY:}")
    private String apiKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        boolean keyConfigured = apiKey != null && !apiKey.isBlank();
        boolean keyMatched = keyConfigured && matches(request.getHeader(HEADER), apiKey);
        if (isProtected(request)) {
            if (!keyConfigured) {
                log.warn("API_KEY 미설정: {} {} 인증이 비활성화되어 있습니다. 운영 환경에서는 반드시 설정하세요.",
                        request.getMethod(), request.getRequestURI());
            } else if (!keyMatched) {
                writeUnauthorized(response);
                return;
            }
        }
        if (keyMatched) {
            request.setAttribute(VERIFIED_CALLER, Boolean.TRUE);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 판정에 쓰는 경로는 컨트롤러를 고르는 경로와 같아야 한다.
     *
     * <p>{@code getRequestURI()} 는 디코딩 전의 원문이다. 예전에는 그것을 그대로 비교해서
     * {@code /api/%68istory} 가 "/api/history 로 시작하지 않는다" 며 통과했고, Spring 은 같은
     * 요청을 디코딩해 이력 컨트롤러로 보냈다 — 키 없이 입력 원문이 전부 나갔다.
     * 디코딩하고, {@code ;} 파라미터를 걷어내고, {@code //} 를 접은 경로로 본다.
     */
    private static final UrlPathHelper PATHS = new UrlPathHelper();

    private boolean isProtected(HttpServletRequest request) {
        String uri = PATHS.getPathWithinApplication(request);
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
