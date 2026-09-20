package com.kaizen.kotona.analyzer.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.kaizen.kotona.analyzer.security.RequestSizeLimitFilter.MAX_BODY_BYTES;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 크기 판정은 본문을 읽기 전에 끝나야 의미가 있다. 그래서 여기서 확인하는 것은
 * "거절되는가" 만이 아니라 "체인으로 넘어가지 않는가" 다 — 넘어가면 이미 다 읽은 뒤다.
 */
class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter();

    @Test
    @DisplayName("한도를 넘는 본문은 읽기 전에 413 으로 돌려보낸다")
    void refusesAnOversizedBodyBeforeReadingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyze");
        request.setContentType("application/json");
        request.setContent(new byte[(int) MAX_BODY_BYTES + 1]);
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("64KB");
        // 여기가 핵심이다. 통과했다면 Jackson 이 본문 전체를 문자열로 만든 다음에 거절했을 것이다.
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("한도까지는 통과시킨다")
    void letsThroughABodyAtTheLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyze");
        request.setContent(new byte[(int) MAX_BODY_BYTES]);
        MockFilterChain chain = new MockFilterChain();

        run(request, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("실제 분석 요청은 한도 근처에도 가지 않는다")
    void aRealRequestIsNowhereNearTheLimit() throws Exception {
        // 가장 큰 정상 요청: 2,000자 일본어. 한도가 정상 사용을 막지 않는지 본다.
        String body = "{\"text\":\"" + "あ".repeat(2000) + "\",\"relationshipType\":\"EXTERNAL\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyze");
        request.setContent(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockFilterChain chain = new MockFilterChain();

        run(request, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(body.getBytes(java.nio.charset.StandardCharsets.UTF_8).length).isLessThan((int) MAX_BODY_BYTES);
    }

    @Test
    @DisplayName("길이를 밝히지 않은 본문은 411 이다")
    void refusesABodyWithoutALength() throws Exception {
        // 미리 잴 수 없으면 다 읽어 봐야 크기를 안다 — 막으려던 상황을 그대로 허용하게 된다.
        // 본문을 넣지 않으면 getContentLengthLong() 이 -1 이다. chunked 요청이 그렇다.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/analyze");
        MockFilterChain chain = new MockFilterChain();

        MockHttpServletResponse response = run(request, chain);

        assertThat(response.getStatus()).isEqualTo(411);
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"GET", "DELETE", "HEAD", "OPTIONS"})
    @DisplayName("본문이 없는 메서드에는 길이를 요구하지 않는다")
    void doesNotAskForALengthWhereThereIsNoBody(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/history/1");
        MockFilterChain chain = new MockFilterChain();

        run(request, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
