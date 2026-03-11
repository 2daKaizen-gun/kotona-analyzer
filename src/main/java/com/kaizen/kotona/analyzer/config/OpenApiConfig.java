package com.kaizen.kotona.analyzer.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kotonaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("KOTONA API Documentation")
                        .description("일본 비즈니스 메일 분석 및 숙어 사전 서비스 API")
                        .version("v1.0.0"));
    }
}
