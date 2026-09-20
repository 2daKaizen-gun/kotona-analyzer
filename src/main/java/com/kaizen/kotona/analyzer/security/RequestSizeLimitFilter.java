package com.kaizen.kotona.analyzer.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * 본문이 너무 크면 읽기 전에 돌려보낸다.
 *
 * <p>DTO 의 {@code @Size} 는 역직렬화가 끝난 뒤에야 판정한다. 그래서 8.6MB 짜리 요청도
 * 전부 읽어 문자열로 만든 다음에 "2000자 이하여야 합니다" 로 거절됐다. 거절은 맞지만
 * 그 전에 본문만큼의 메모리를 쓴다. 레이트 리미터는 요청 수를 세지 바이트를 세지 않는다.
 *
 * <p>한도는 64KB 로, 이 API 가 받는 가장 큰 요청(2,000자 일본어 + 나머지 필드)의
 * 열 배쯤 된다. 정상 요청이 여기 걸릴 일은 없다.
 *
 * <p>길이를 밝히지 않은 본문(chunked)은 411 로 돌려보낸다. 미리 잴 수 없는 본문은
 * 결국 다 읽어 봐야 크기를 알 수 있어서, 막으려는 상황을 그대로 허용하게 된다.
 * 브라우저의 fetch 도 curl 도 Swagger 도 문자열 본문에는 Content-Length 를 붙인다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    /** 허용하는 본문의 최대 바이트. */
    static final long MAX_BODY_BYTES = 64L * 1024;

    /** 본문을 실어 보내는 메서드. 나머지는 본문이 없으므로 길이를 묻지 않는다. */
    private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (METHODS_WITH_BODY.contains(request.getMethod())) {
            long declared = request.getContentLengthLong();

            if (declared > MAX_BODY_BYTES) {
                log.warn("본문이 너무 큼: {} bytes ({} {})", declared, request.getMethod(), request.getRequestURI());
                refuse(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "요청 본문이 너무 큽니다. " + (MAX_BODY_BYTES / 1024) + "KB 이하로 보내 주세요.");
                return;
            }

            if (declared < 0) {
                refuse(response, HttpServletResponse.SC_LENGTH_REQUIRED,
                        "Content-Length 헤더가 필요합니다.");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /** 필터는 컨트롤러 밖이라 GlobalExceptionHandler 가 닿지 않는다. 같은 모양으로 직접 쓴다. */
    private void refuse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
