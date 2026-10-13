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

    /**
     * 호출 한 건을 기다려 줄 기본 한계(밀리초). application.yaml 의 기본값과 같아야 한다 — 테스트가 맞대어 본다.
     *
     * <p>두 숫자 사이에 있어야 한다. 아래로는 관측된 가장 느린 정상 호출(79초)이 있고, 위로는
     * 프론트가 포기하는 시점(kotona-web {@code backend.ts} 의 110초)이 있다. 예전 값 180초는
     * 위쪽을 넘었다 — 프론트가 "다시 시도하세요" 를 띄운 뒤에도 여기서는 기다렸다가 결과를 저장했고,
     * 사용자가 다시 누르면 쿼터가 두 번 나갔다.
     */
    public static final int DEFAULT_TIMEOUT_MS = 100_000;

    @Value("${gemini.timeout-ms:" + DEFAULT_TIMEOUT_MS + "}")
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
     * <p>기본값을 어디에 두는지는 {@link #DEFAULT_TIMEOUT_MS} 에 적었다 — 정상 호출을 끊지 않으면서,
     * 호출한 쪽이 이미 떠난 뒤까지 기다리지는 않는 선.
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
