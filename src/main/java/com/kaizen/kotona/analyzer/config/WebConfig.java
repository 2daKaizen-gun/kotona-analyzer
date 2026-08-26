package com.kaizen.kotona.analyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * CORS 설정(프론트엔드 연동) + /analyze Rate Limit 인터셉터 등록.
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** 리버스 프록시(nginx, ALB 등) 뒤에 있을 때만 true. 기본은 직접 노출 가정. */
    @Value("${app.trust-forwarded-for:false}")
    private boolean trustForwardedFor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(trustForwardedFor))
                .addPathPatterns("/analyze");
    }

    /**
     * 허용할 프론트엔드 오리진 목록.
     * 와일드카드로 열어두면 아무 사이트나 사용자의 브라우저를 통해
     * /analyze(쿼터 소모)와 /api/history(입력 원문)를 호출할 수 있다.
     */
    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:5173}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (Arrays.asList(allowedOrigins).contains("*")) {
            log.warn("CORS 가 모든 오리진에 열려 있습니다. 운영 환경에서는 "
                    + "app.cors.allowed-origins(CORS_ALLOWED_ORIGINS)로 좁히세요.");
        }

        registry.addMapping("/**")
                // 정확한 오리진뿐 아니라 https://*.vercel.app 같은 패턴도 받는다.
                .allowedOriginPatterns(allowedOrigins)
                // PUT 엔드포인트는 없으므로 허용하지 않는다.
                .allowedMethods("GET", "POST", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }
}
