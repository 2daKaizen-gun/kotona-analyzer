package com.kaizen.kotona.analyzer.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

@Configuration
public class GeminiConfig {

    @Value("${gemini.project-id}")
    private String projectId;

    @Value("${gemini.location:us-central1}")
    private String location;

    // 추가: 실행 시 주입받은 키 경로 (기본값은 resources 내부)
    @Value("${google.key.path:classpath:google-key.json}")
    private String keyPath;

    @Bean
    public VertexAI vertexAI() throws IOException {
        // [수정 핵심] ClassPathResource 대신 ResourceLoader를 사용합니다.
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(keyPath);

        // credentials 생성
        GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream())
                .createScoped("https://www.googleapis.com/auth/cloud-platform");

        // 정보 직접 세팅
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .setCredentials(credentials)
                .setTransport(Transport.REST) // gRPC 대신 REST를 사용하도록 강제
                .build();
    }

    @Bean
    public GenerativeModel generativeModel(VertexAI vertexAI) {
        // 복잡한 JSON 생성위해 타임아웃, 토큰 제한 설정
        GenerationConfig config = GenerationConfig.newBuilder()
                .setMaxOutputTokens(2048) // 토큰 늘리기
                .setTemperature(0.7f) // 창의적인 답장 생성 위한 온도 조절
                .setResponseMimeType("application/json") // JSON 포멧 강제 설정
                .build();

        return new GenerativeModel("gemini-2.0-flash", vertexAI)
                .withGenerationConfig(config);
    }
}
