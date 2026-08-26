package com.kaizen.kotona.analyzer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS 허용(프론트엔드 연동, issue #27) + /analyze Rate Limit 인터셉터 등록.
 */
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

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
