package com.kaizen.kotona.analyzer.client;

import tools.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.utils.NuanceSchemaFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 Gemini 를 부른다. {@code ./gradlew liveTest} 로만 돌고, 평소 {@code test} 에서는 빠진다.
 *
 * <p>나머지 테스트는 {@link NuanceModelClient} 를 가짜로 바꿔 놓고 돈다. 덕분에 서비스 로직은
 * 무료로, 빠르게, 결정적으로 검증되지만 — 그 seam 의 실제 구현인 {@link GenAiNuanceModelClient}
 * 자체는 아무도 실행해 보지 않는다는 뜻이기도 하다. SDK 호출 방식이 바뀌거나 모델 이름이
 * 사라지면 여기서만 드러난다.
 *
 * <p>CI 에 넣지 않는 이유는 매 푸시마다 쿼터를 쓰기 때문이다. 대신 SDK 나 모델 설정을 건드린
 * 뒤에는 이 테스트를 손으로 한 번 돌린다. 비용은 호출 한 번, 20초 안팎이다.
 *
 * <p>DB 는 필요 없다. Spring 컨텍스트를 띄우지 않고 SDK 경계만 확인한다.
 */
@Tag("live")
class GenAiNuanceModelClientLiveTest {

    /** 실제 분석 요청과 같은 모양이되, 출력 토큰이 적게 나오도록 짧은 문장을 쓴다. */
    private static final String PROMPT = """
            # Relationship Context: INTERNAL
            (INTERNAL = 사내, EXTERNAL = 사외/고객사, INTERVIEW = 면접)

            # User Input
            ご確認をお願いします。
            """;

    private static final String SYSTEM_INSTRUCTION =
            "You are a Business Japanese expert. Score the sentence and answer with the given schema. "
                    + "Explanations in Korean, Japanese fields in Japanese.";

    @Test
    @DisplayName("실제 모델이 스키마대로 답하고, 그 답이 우리 DTO 로 읽힌다")
    void callsTheRealModelAndComesBackAsOurType() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey)
                .as("GEMINI_API_KEY 가 있어야 한다 — .env 가 아니라 환경변수로 넘긴다")
                .isNotBlank();

        Schema schema = Schema.fromJson(NuanceSchemaFactory.build(NuanceResponseDTO.class).toString());
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                .temperature(0.7f)
                .maxOutputTokens(8000)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();

        NuanceModelClient client =
                new GenAiNuanceModelClient(Client.builder().apiKey(apiKey).build());

        String raw = client.generate(System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.6-flash"),
                PROMPT, config);

        // 응답이 왔는가보다, 우리가 읽을 수 있는 모양으로 왔는가가 중요하다.
        assertThat(raw).isNotBlank();
        NuanceResponseDTO parsed = new ObjectMapper().readValue(raw, NuanceResponseDTO.class);
        assertThat(parsed.totalScore()).isBetween(0, 100);
        assertThat(parsed.metrics()).isNotNull();
        assertThat(parsed.riskAnalysis()).isNotNull();
        assertThat(parsed.smartReplies()).isNotEmpty();
    }
}
