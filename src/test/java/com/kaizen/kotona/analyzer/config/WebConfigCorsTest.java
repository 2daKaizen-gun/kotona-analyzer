package com.kaizen.kotona.analyzer.config;

import com.kaizen.kotona.analyzer.controller.HealthCheckController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS 설정은 어느 사이트가 방문자의 브라우저를 빌려 이 API 를 부를 수 있는지를 정한다.
 * 너무 좁으면 프론트가 깨지고, 너무 넓으면 아무 사이트나 방문자 대신 쿼터를 쓰고
 * 이력을 읽을 수 있다. 앞의 실수는 바로 보이지만 뒤의 실수는 아무 증상이 없다.
 * 그래서 "거절되는가" 를 "허용되는가" 만큼 본다.
 */
class WebConfigCorsTest {

    private static final String ALLOWED = "https://kotona-web.vercel.app";

    @Nested
    @WebMvcTest(HealthCheckController.class)
    @TestPropertySource(properties = "app.cors.allowed-origins=" + ALLOWED + ",https://*.preview.example.com")
    @DisplayName("오리진을 지정했을 때")
    class Configured {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("허용한 오리진에는 CORS 헤더를 준다")
        void allowsTheConfiguredOrigin() throws Exception {
            mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, ALLOWED))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
        }

        @Test
        @DisplayName("목록에 없는 오리진은 거절한다")
        void refusesAnUnlistedOrigin() throws Exception {
            // 거절이 조용하면 설정이 틀려도 아무도 모른다. 명시적으로 403 이어야 한다.
            mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "https://evil.example.com"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }

        @Test
        @DisplayName("허용한 도메인을 흉내 낸 오리진도 거절한다")
        void refusesALookalikeOrigin() throws Exception {
            // 접두사나 접미사만 비교하는 실수를 막는다
            mockMvc.perform(get("/api/health")
                            .header(HttpHeaders.ORIGIN, "https://kotona-web.vercel.app.evil.com"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/health")
                            .header(HttpHeaders.ORIGIN, "https://evil-kotona-web.vercel.app"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("패턴은 지정한 하위 도메인만 허용한다")
        void matchesPatternsOnlyWithinTheirDomain() throws Exception {
            // Vercel 미리보기 배포는 매번 주소가 달라서 패턴이 필요하다. 대신 그 범위를 넘으면 안 된다.
            mockMvc.perform(get("/api/health")
                            .header(HttpHeaders.ORIGIN, "https://pr-12.preview.example.com"))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/api/health")
                            .header(HttpHeaders.ORIGIN, "https://preview.example.com.evil.com"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("사전 요청에서 실제로 쓰는 메서드를 허용한다")
        void allowsTheMethodsTheFrontendUses() throws Exception {
            // PUT 이 빠지면 사전 수정이, DELETE 가 빠지면 삭제가 브라우저에서만 실패한다
            for (String method : new String[] {"GET", "POST", "PUT", "DELETE"}) {
                mockMvc.perform(options("/api/phrases/1")
                                .header(HttpHeaders.ORIGIN, ALLOWED)
                                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
                        .andExpect(status().isOk())
                        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED));
            }
        }

        @Test
        @DisplayName("쓰지 않는 메서드는 허용하지 않는다")
        void refusesMethodsNothingUses() throws Exception {
            mockMvc.perform(options("/api/phrases/1")
                            .header(HttpHeaders.ORIGIN, ALLOWED)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("API 키 헤더를 보낼 수 있게 허용한다")
        void allowsTheApiKeyHeader() throws Exception {
            // 이게 막히면 인증이 켜진 순간 브라우저 요청이 전부 실패한다
            mockMvc.perform(options("/analyze")
                            .header(HttpHeaders.ORIGIN, ALLOWED)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-API-KEY, Content-Type"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @WebMvcTest(HealthCheckController.class)
    @DisplayName("오리진을 지정하지 않았을 때")
    class Defaults {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("로컬 개발 포트는 허용한다")
        void allowsLocalDevelopmentPorts() throws Exception {
            // 기본값이 없으면 로컬에서 프론트를 띄우자마자 CORS 오류가 난다
            for (String origin : new String[] {"http://localhost:3000", "http://localhost:5173"}) {
                mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, origin))
                        .andExpect(status().isOk());
            }
        }

        @Test
        @DisplayName("기본값은 와일드카드가 아니다")
        void isNotAWildcardByDefault() throws Exception {
            // 설정을 빠뜨린 배포가 전부 열리는 일을 막는다
            mockMvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "https://anywhere.example.com"))
                    .andExpect(status().isForbidden());
        }
    }
}
