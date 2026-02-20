package com.kaizen.kotona.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

@Configuration
public class GeminiConfig {

    @Value("${gemini.project-id}")
    private String projectId;

    @Value("${gemini.location:us-central1}")
    private String location;

    @Bean
    public VertexAI vertexAI() throws IOException {
        // 리소스 폴더에서 직접 JSON 파일을 읽어옴
        ClassPathResource resource = new ClassPathResource("google-key.json");
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
        return new GenerativeModel("gemini-2.5-flash", vertexAI);
    }
}
