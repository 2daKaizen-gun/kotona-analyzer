package com.kaizen.kotona.analyzer.evaluation;

import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.kaizen.kotona.analyzer.client.GenAiNuanceModelClient;
import com.kaizen.kotona.analyzer.client.NuanceModelClient;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.utils.NuanceSchemaFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모델이 라벨과 얼마나 맞는지 잰다. {@code ./gradlew evalTest} 로만 돈다.
 *
 * <p>규칙 계층은 CI 에서 매번 재지만, 모델은 그럴 수 없다 — 문장 하나에 20~80초가 걸리고
 * 무료 티어 쿼터를 쓴다. 그래서 수동 실행으로 두고, 숫자를 단언하는 대신 보고한다.
 * 몇 퍼센트여야 합격인지 정할 근거가 아직 없기 때문이다. 합격선을 지어내는 것보다
 * 추세를 눈으로 보는 편이 정직하다.
 *
 * <p>재는 것은 리스크 등급 하나다. 총점은 비교할 정답이 없고(라벨에 "73점" 같은 값은 없다),
 * 등급은 세 값 중 하나라 일치 여부를 말할 수 있다.
 *
 * <p>기본은 라벨 전체. {@code -DevalLimit=5} 로 줄여서 쿼터를 아낄 수 있다.
 */
@Tag("eval")
class ModelEvaluationLiveTest {

    private static final String SYSTEM_INSTRUCTION = """
            You are a "Business Japanese Communication Expert".
            Judge the risk of a soft rejection: SAFE, CAUTION or DANGER.
            Answer with the given schema. Explanations in Korean, Japanese fields in Japanese.
            """;

    @Test
    @DisplayName("모델의 리스크 판정을 라벨과 맞대어 본다")
    void reportsAgreementWithTheLabels() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 가 있어야 한다").isNotBlank();

        List<EvaluationSet.Row> rows = EvaluationSet.load().rows();
        int limit = Integer.getInteger("evalLimit", rows.size());
        rows = rows.subList(0, Math.min(limit, rows.size()));

        Schema schema = Schema.fromJson(NuanceSchemaFactory.build(NuanceResponseDTO.class).toString());
        GenerateContentConfig config = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                // 채점은 판정이므로 표본을 흔들지 않는다. 운영 설정과 같다.
                .temperature(0.0f)
                .maxOutputTokens(8000)
                .responseMimeType("application/json")
                .responseSchema(schema)
                .build();
        NuanceModelClient client = new GenAiNuanceModelClient(Client.builder().apiKey(apiKey).build());
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.6-flash");
        ObjectMapper mapper = new ObjectMapper();

        List<String> mismatches = new ArrayList<>();
        int asked = 0;
        int agreed = 0;
        int failed = 0;

        for (EvaluationSet.Row row : rows) {
            String prompt = """
                    # Relationship Context: %s

                    # User Input
                    %s
                    """.formatted(row.relationship().name(), row.text());
            String raw;
            try {
                raw = client.generate(model, prompt, config);
            } catch (RuntimeException e) {
                // 503(과부하)과 429(쿼터)는 평가의 결과가 아니라 평가를 막은 사정이다.
                failed++;
                System.out.printf("  %-14s 호출 실패: %s%n", row.id(), e.getClass().getSimpleName());
                continue;
            }
            asked++;
            String got = mapper.readValue(raw, NuanceResponseDTO.class).riskAnalysis().riskLevel();
            if (row.risk().equalsIgnoreCase(got)) {
                agreed++;
            } else {
                mismatches.add("  %-14s 라벨 %-7s 모델 %-7s  %s%n      근거: %s"
                        .formatted(row.id(), row.risk(), got, row.text(), row.basis()));
            }
        }

        System.out.printf("%n=== 모델 리스크 판정 일치율 ===%n");
        System.out.printf("물어본 문장 %d건 중 %d건 일치 (%s)%s%n",
                asked, agreed,
                asked == 0 ? "측정 불가" : "%.0f%%".formatted(100.0 * agreed / asked),
                failed > 0 ? ", 호출 실패 %d건".formatted(failed) : "");
        mismatches.forEach(System.out::println);

        // 합격선은 두지 않는다. 다만 한 건도 묻지 못했다면 측정 자체가 없던 일이다.
        assertThat(asked).as("모델에 한 건도 묻지 못했다 — 쿼터나 업스트림 상태를 확인할 것").isPositive();
    }
}
