package com.kaizen.kotona.analyzer.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사전 쓰기가 추가되면서 필터가 경로뿐 아니라 메서드도 보게 됐다.
 * 조회는 계속 열려 있고 쓰기만 막히는지, 기존 보호 경로는 그대로인지 확인한다.
 */
class ApiKeyAuthFilterTest {

    private static final String KEY = "secret";

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"POST, /api/phrases", "PUT, /api/phrases/1", "DELETE, /api/phrases/1"})
    @DisplayName("키가 설정되면 사전 쓰기 요청은 헤더 없이 401")
    void phraseWritesRequireKey(String method, String uri) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(filterWithKey(KEY), new MockHttpServletRequest(method, uri), chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({"GET, /api/phrases", "GET, /api/phrases/search", "OPTIONS, /api/phrases"})
    @DisplayName("사전 조회와 CORS preflight 는 키 없이 통과")
    void phraseReadsStayOpen(String method, String uri) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run(filterWithKey(KEY), new MockHttpServletRequest(method, uri), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("올바른 키가 있으면 쓰기 요청도 통과")
    void phraseWriteWithKeyPasses() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/phrases");
        request.addHeader("X-API-KEY", KEY);
        MockFilterChain chain = new MockFilterChain();

        run(filterWithKey(KEY), request, chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("기존 보호 경로는 메서드와 무관하게 그대로 보호된다")
    void existingPrefixesStayProtectedForReads() throws Exception {
        MockHttpServletResponse response = run(filterWithKey(KEY),
                new MockHttpServletRequest("GET", "/api/history"), new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("키가 설정되지 않으면 쓰기 요청도 통과한다 (로컬 개발)")
    void noKeyConfiguredLetsWritesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run(filterWithKey(""), new MockHttpServletRequest("DELETE", "/api/phrases/1"), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private ApiKeyAuthFilter filterWithKey(String key) {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter();
        ReflectionTestUtils.setField(filter, "apiKey", key);
        return filter;
    }

    private MockHttpServletResponse run(ApiKeyAuthFilter filter, MockHttpServletRequest request,
                                        MockFilterChain chain) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }
}
