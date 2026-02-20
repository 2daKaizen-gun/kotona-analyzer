package com.kaizen.kotona.config;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key}")
    // .env에서 읽어온 키
    private String apiKey;

    @Value("${gemini.project-id}")
    private String projectId;

    @Value("${gemini.location:us-central1}")
    private String location;

    @Bean
    public VertexAI vertexAI() {
        // VertexAI 클라이언트 생성
        return new VertexAI.Builder()
                .setProjectId(projectId)
                .setLocation(location)
                .setApiKey(apiKey)
                .build();
    }

    @Bean
    public GenerativeModel generativeModel(VertexAI vertexAI) {
        return new GenerativeModel("gemini-flash-latest", vertexAI);
    }
}
