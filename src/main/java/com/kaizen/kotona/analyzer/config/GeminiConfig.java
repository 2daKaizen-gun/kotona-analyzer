package com.kaizen.kotona.analyzer.config;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
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

    @Value("${gemini.timeout-ms:180000}")
    private int timeoutMs;

    @Bean
    public Client genAiClient() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY 가 설정되지 않았습니다. .env 또는 환경변수에 추가하세요. "
                            + "(https://aistudio.google.com/apikey 에서 발급)");
        }
        return Client.builder().apiKey(apiKey).httpOptions(httpOptions(timeoutMs)).build();
    }

    /**
     * 호출 한 건을 기다려 줄 한계.
     *
     * <p>SDK 기본값은 무한 대기다. 업스트림이 응답을 멈추면 그 요청을 처리하던 톰캣 스레드가
     * 그대로 붙잡히고, 같은 일이 몇 번 겹치면 서비스 전체가 느려지는 쪽으로 번진다 —
     * 모델 호출은 평소 20~30초, 느린 꼬리가 79초까지 관측된 작업이라 "곧 오겠지" 와
     * "오지 않는다" 를 호출 쪽에서 구분할 방법이 없다. 그래서 상한을 둔다.
     *
     * <p>기본 3분은 관측된 꼬리의 두 배 남짓이다 — 정상 호출을 끊지 않으면서 멈춘 호출은
     * 포기하는 선.
     */
    public static HttpOptions httpOptions(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalStateException(
                    "gemini.timeout-ms 는 0 보다 커야 합니다. 무한 대기는 스레드를 영구히 붙잡습니다.");
        }
        return HttpOptions.builder().timeout(timeoutMs).build();
    }

    /** 응답 강제용 JSON 스키마. DTO 트리에서 파생되므로 기동 시 한 번만 만들어 재사용한다. */
    @Bean
    public Schema nuanceResponseSchema() {
        return Schema.fromJson(NuanceSchemaFactory.build(NuanceResponseDTO.class).toString());
    }
}
