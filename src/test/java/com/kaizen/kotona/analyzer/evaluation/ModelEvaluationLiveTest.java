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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
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
 * <p><b>이것은 합격/불합격을 가르는 테스트가 아니라 측정 도구다.</b> 쿼터가 비어 호출이
 * 전부 실패해도 빌드를 깨지 않는다 — 그건 코드의 문제가 아니라 그날의 사정이고, 기록은
 * 남으므로 다음 실행이 이어 간다. 읽어야 할 것은 초록불이 아니라 출력된 숫자다.
 *
 * <p>재는 것은 리스크 등급 하나다. 총점은 비교할 정답이 없고(라벨에 "73점" 같은 값은 없다),
 * 등급은 세 값 중 하나라 일치 여부를 말할 수 있다.
 *
 * <p>무료 티어로는 하루에 전부 묻지 못한다. 그래서 받은 답을
 * {@code src/test/resources/evaluation/model-answers.json} 에 적어 두고, 다음 실행은
 * 아직 답이 없는 것부터 묻는다. 며칠 돌리면 전체가 채워지고, 그 사이의 일치율은
 * "지금까지 받은 답 기준" 으로 보고된다.
 *
 * <p>{@code -DevalLimit=5} 로 한 번에 묻는 수를 줄일 수 있다.
 */
@Tag("eval")
// 쿼터가 조금씩만 들어오므로 어느 쪽을 먼저 묻는지가 그날의 측정을 정한다.
// 리스크 평가를 먼저 둔다 — 문장당 1회로 34개의 자료점을 모으는 쪽이, 쌍당 2회로
// 7개를 모으는 쪽보다 같은 호출 수에서 더 많이 알려 준다.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelEvaluationLiveTest {

    /**
     * 연속 실패 허용치. 쿼터가 비면 남은 호출도 전부 같은 이유로 실패하므로,
     * 세 번 연달아 막히면 그만둔다 — 실제로 33 건을 모두 시도하며 3분을 버린 적이 있다.
     */
    private static final int CONSECUTIVE_FAILURE_LIMIT = 3;

    private static final String SYSTEM_INSTRUCTION = """
            You are a "Business Japanese Communication Expert".
            Judge the risk of a soft rejection: SAFE, CAUTION or DANGER.
            Answer with the given schema. Explanations in Korean, Japanese fields in Japanese.
            """;

    @Test
    @Order(2)
    @DisplayName("모델이 문장의 순서를 뒤집지 않는지 본다")
    void respectsTheOrderingPairs() throws Exception {
        // 지표에는 비교할 정답 점수가 없지만 순서에는 있다. 쌍마다 두 번 부르므로 비싸다 —
        // -DevalLimit 으로 쌍 수를 줄일 수 있다.
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY 가 있어야 한다").isNotBlank();

        List<EvaluationSet.OrderingPair> pairs = EvaluationSet.orderingPairs();
        int limit = Integer.getInteger("evalLimit", pairs.size());
        pairs = pairs.subList(0, Math.min(limit, pairs.size()));

        Judge judge = new Judge(apiKey);
        EvaluationLog log = EvaluationLog.load();
        List<String> inverted = new ArrayList<>();
        int compared = 0;
        int asked = 0;

        int consecutiveFailures = 0;
        for (EvaluationSet.OrderingPair pair : pairs) {
            if (consecutiveFailures >= CONSECUTIVE_FAILURE_LIMIT) {
                System.out.printf("  연속 %d회 실패 — 쿼터가 비었다고 보고 중단한다.%n", consecutiveFailures);
                break;
            }
            String seen = log.recorded("order", pair.id(), judge.model());
            Integer low;
            Integer high;
            if (seen != null) {
                String[] parts = seen.split("/");
                low = Integer.valueOf(parts[0]);
                high = Integer.valueOf(parts[1]);
            } else {
                low = judge.axis(pair.lower(), pair.axis());
                high = judge.axis(pair.higher(), pair.axis());
                if (low != null && high != null) {
                    log.record("order", pair.id(), judge.model(), low + "/" + high);
                    asked++;
                }
            }
            if (low == null || high == null) {
                consecutiveFailures++;
                System.out.printf("  %-18s 호출 실패로 건너뜀 (다음 실행에서 다시 묻는다)%n", pair.id());
                continue;
            }
            consecutiveFailures = 0;
            compared++;
            System.out.printf("  %-18s %-12s %2d → %2d%s%n", pair.id(), pair.axis(), low, high,
                    high >= low ? "" : "   뒤집힘");
            if (high < low) {
                inverted.add("  %s (%s): 「%s」=%d 가 「%s」=%d 보다 높다%n      근거: %s"
                        .formatted(pair.id(), pair.axis(), pair.lower(), low, pair.higher(), high, pair.basis()));
            }
        }

        log.save();
        System.out.printf("%n=== 모델 순서 준수 ===%n비교한 쌍 %d/%d 중 %d 쌍이 순서를 지켰다 (이번에 새로 물은 쌍 %d)%n",
                compared, pairs.size(), compared - inverted.size(), asked);
        inverted.forEach(System.out::println);
        if (compared == 0) {
            System.out.println("오늘은 한 쌍도 받지 못했다 — 쿼터가 돌아오면 같은 명령을 다시 돌리면 이어서 묻는다.");
        }
    }

    @Test
    @Order(1)
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
        EvaluationLog log = EvaluationLog.load();

        List<String> mismatches = new ArrayList<>();
        int compared = 0;
        int askedNow = 0;
        int agreed = 0;
        int failed = 0;

        int consecutiveFailures = 0;
        for (EvaluationSet.Row row : rows) {
            if (consecutiveFailures >= CONSECUTIVE_FAILURE_LIMIT) {
                System.out.printf("  연속 %d회 실패 — 쿼터가 비었다고 보고 중단한다. 남은 %d건은 다음 실행에서 묻는다.%n",
                        consecutiveFailures, rows.size() - compared - failed);
                break;
            }
            String prompt = """
                    # Relationship Context: %s

                    # User Input
                    %s
                    """.formatted(row.relationship().name(), row.text());
            String got = log.recorded("risk", row.id(), model);
            if (got == null) {
                try {
                    got = mapper.readValue(client.generate(model, prompt, config), NuanceResponseDTO.class)
                            .riskAnalysis().riskLevel();
                } catch (RuntimeException e) {
                    // 503(과부하)과 429(쿼터)는 평가의 결과가 아니라 평가를 막은 사정이다.
                    failed++;
                    consecutiveFailures++;
                    System.out.printf("  %-14s 호출 실패: %s (다음 실행에서 다시 묻는다)%n",
                            row.id(), e.getClass().getSimpleName());
                    continue;
                }
                log.record("risk", row.id(), model, got);
                askedNow++;
                consecutiveFailures = 0;
            }
            compared++;
            if (row.risk().equalsIgnoreCase(got)) {
                agreed++;
            } else {
                mismatches.add("  %-14s 라벨 %-7s 모델 %-7s  %s%n      근거: %s"
                        .formatted(row.id(), row.risk(), got, row.text(), row.riskBasis()));
            }
        }

        log.save();
        System.out.printf("%n=== 모델 리스크 판정 일치율 ===%n");
        System.out.printf("답을 받은 문장 %d/%d 중 %d건 일치 (%s). 이번에 새로 물은 문장 %d건%s%n",
                compared, rows.size(), agreed,
                compared == 0 ? "측정 불가" : "%.0f%%".formatted(100.0 * agreed / compared),
                askedNow,
                failed > 0 ? ", 호출 실패 %d건".formatted(failed) : "");
        mismatches.forEach(System.out::println);
        if (compared == 0) {
            System.out.println("오늘은 한 건도 받지 못했다 — 쿼터가 돌아오면 같은 명령을 다시 돌리면 이어서 묻는다.");
        }
    }

    /** 한 문장을 모델에 물어 지표 하나를 꺼낸다. 호출이 실패하면 null 이다. */
    private static final class Judge {
        private final NuanceModelClient client;
        private final GenerateContentConfig config;
        private final String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.6-flash");
        private final ObjectMapper mapper = new ObjectMapper();

        Judge(String apiKey) {
            Schema schema = Schema.fromJson(NuanceSchemaFactory.build(NuanceResponseDTO.class).toString());
            this.config = GenerateContentConfig.builder()
                    .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_INSTRUCTION)))
                    .temperature(0.0f)
                    .maxOutputTokens(8000)
                    .responseMimeType("application/json")
                    .responseSchema(schema)
                    .build();
            this.client = new GenAiNuanceModelClient(Client.builder().apiKey(apiKey).build());
        }

        String model() {
            return model;
        }

        Integer axis(String text, String axis) {
            String prompt = """
                    # Relationship Context: EXTERNAL

                    # User Input
                    %s
                    """.formatted(text);
            try {
                NuanceResponseDTO parsed = mapper.readValue(client.generate(model, prompt, config), NuanceResponseDTO.class);
                return switch (axis) {
                    case "politeness" -> parsed.metrics().politeness();
                    case "indirectness" -> parsed.metrics().indirectness();
                    case "etiquette" -> parsed.metrics().etiquette();
                    default -> throw new IllegalArgumentException("모르는 축: " + axis);
                };
            } catch (RuntimeException e) {
                return null;
            }
        }
    }
}
