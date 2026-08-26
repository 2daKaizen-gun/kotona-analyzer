package com.kaizen.kotona.analyzer.config;

import com.google.genai.Client;
import com.google.genai.types.Schema;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.utils.NuanceSchemaFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Gemini(Google AI Studio) 설정.
 *
 * <p>Vertex AI 가 아니라 AI Studio 경로를 쓴다. 서비스 계정 JSON 키도, GCP 프로젝트도,
 * 결제 계정도 필요 없고 API 키 하나로 끝난다(무료 티어 사용 가능).
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Bean
    public Client genAiClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY 가 설정되지 않았습니다. .env 또는 환경변수에 추가하세요. "
                            + "(https://aistudio.google.com/apikey 에서 발급)");
        }
        return Client.builder().apiKey(apiKey).build();
    }

    /** 응답 강제용 JSON 스키마. DTO 트리에서 파생되므로 기동 시 한 번만 만들어 재사용한다. */
    @Bean
    public Schema nuanceResponseSchema() {
        return Schema.fromJson(NuanceSchemaFactory.build(NuanceResponseDTO.class).toString());
    }
}
