package com.kaizen.kotona.analyzer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.types.Schema;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 이 스키마가 Gemini 에게 응답 형태를 강제한다. 여기가 틀어지면 분석이 통째로 실패하는데,
 * 실패가 컴파일이 아니라 모델 호출 시점에 드러나므로 배포 후에야 알게 된다.
 *
 * <p>구조를 통째로 비교하지 않는다. DTO 에 필드를 하나 더해도 깨지지 않되,
 * 스키마가 쓸모없어지는 변화는 잡히도록 성질만 본다.
 */
class NuanceSchemaFactoryTest {

    private static ObjectNode schema;

    @BeforeAll
    static void buildOnce() {
        schema = NuanceSchemaFactory.build(NuanceResponseDTO.class);
    }

    @Test
    @DisplayName("Gemini 가 받아들이는 스키마를 만든다")
    void producesSomethingGeminiAccepts() {
        // 최종 관문. 여기서 거절되면 기동은 되지만 모든 분석이 실패한다.
        assertThatCode(() -> Schema.fromJson(schema.toString())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("트리 순회가 실제로 중첩까지 닿는다")
    void theWalkReachesNestedNodes() {
        // 아래 두 검사는 "위반 목록이 비어 있음" 으로 통과한다. 순회가 아무 데도 닿지 않으면
        // 그 목록은 언제나 비어 있고, 검사는 통과하면서 아무것도 지키지 않게 된다.
        List<String> visited = new ArrayList<>();
        walk(schema, "", node -> visited.add(node.path()));

        assertThat(visited).contains(
                "(root)",
                ".metrics",
                ".sentiment.honne",
                ".smartReplies[]");
    }

    @Test
    @DisplayName("Gemini 가 모르는 키를 남기지 않는다")
    void stripsKeysGeminiDoesNotUnderstand() {
        // responseSchema 는 OpenAPI 3.0 의 부분집합만 받는다.
        // $ref 가 남으면 중첩 객체가 통째로 전달되지 않는다.
        List<String> offenders = new ArrayList<>();
        walk(schema, "", node -> {
            for (String key : List.of("$schema", "additionalProperties", "$ref", "$defs", "definitions")) {
                if (node.value().has(key)) {
                    offenders.add(node.path() + "." + key);
                }
            }
        });

        assertThat(offenders).isEmpty();
    }

    @Test
    @DisplayName("모든 객체의 필드를 필수로 지정한다")
    void marksEveryPropertyRequired() {
        // 빠뜨린 필드는 DTO 에 null 로 들어가고, 화면에는 빈 칸으로 나온다.
        List<String> incomplete = new ArrayList<>();
        walk(schema, "", node -> {
            JsonNode properties = node.value().get("properties");
            if (properties == null) return;

            JsonNode required = node.value().get("required");
            List<String> names = new ArrayList<>();
            properties.fieldNames().forEachRemaining(names::add);

            List<String> requiredNames = new ArrayList<>();
            if (required != null) required.forEach(entry -> requiredNames.add(entry.asText()));

            if (!requiredNames.containsAll(names)) {
                incomplete.add(node.path());
            }
        });

        assertThat(incomplete).isEmpty();
    }

    @Test
    @DisplayName("@JsonProperty 로 바꾼 이름을 그대로 쓴다")
    void usesTheRenamedFieldNames() {
        // 모델이 keigoCheck 로 답하면 역직렬화에서 조용히 빠진다
        JsonNode evaluation = schema.path("properties").path("evaluation").path("properties");

        assertThat(evaluation.has("keigo_check")).isTrue();
        assertThat(evaluation.has("cushion_phrase_check")).isTrue();
        assertThat(evaluation.has("keigoCheck")).isFalse();
    }

    @Test
    @DisplayName("필드 설명이 스키마에 실려 모델에게 전달된다")
    void carriesTheFieldDescriptions() {
        // 프롬프트에서 스키마 블록을 지운 근거가 이것이다.
        // 설명이 빠지면 모델은 허용값(SAFE/CAUTION/DANGER 등)을 알 길이 없다.
        assertThat(schema.path("properties").path("category").path("description").asText())
                .contains("INTERNAL_CHAT");
        assertThat(schema.path("properties").path("riskAnalysis").path("properties")
                .path("riskLevel").path("description").asText())
                .contains("DANGER");
    }

    @Test
    @DisplayName("중첩 객체와 배열까지 펼쳐서 담는다")
    void expandsNestedObjectsAndArrays() {
        // 배열 항목이 비어 있으면 모델이 임의의 모양으로 답한다
        JsonNode smartReplies = schema.path("properties").path("smartReplies");
        assertThat(smartReplies.path("type").asText()).isEqualTo("array");
        assertThat(smartReplies.path("items").path("properties").has("content")).isTrue();

        JsonNode honne = schema.path("properties").path("sentiment")
                .path("properties").path("honne").path("properties");
        assertThat(honne.has("tatemae")).isTrue();
        assertThat(honne.has("trueIntent")).isTrue();
    }

    @Test
    @DisplayName("숫자 필드의 타입이 정수와 실수로 나뉜다")
    void distinguishesIntegersFromDecimals() {
        // confidence 가 integer 로 나가면 0.85 를 돌려줄 수 없다
        assertThat(schema.path("properties").path("totalScore").path("type").asText())
                .isEqualTo("integer");
        assertThat(schema.path("properties").path("sentiment").path("properties")
                .path("confidence").path("type").asText())
                .isEqualTo("number");
    }

    // --- 헬퍼 ---

    private record Visited(String path, ObjectNode value) {
    }

    /** 스키마 트리의 모든 객체 노드를 훑는다. */
    private void walk(ObjectNode node, String path, java.util.function.Consumer<Visited> visitor) {
        visitor.accept(new Visited(path.isEmpty() ? "(root)" : path, node));

        JsonNode properties = node.get("properties");
        if (properties != null) {
            properties.properties().forEach(entry -> {
                if (entry.getValue() instanceof ObjectNode child) {
                    walk(child, path + "." + entry.getKey(), visitor);
                }
            });
        }
        if (node.get("items") instanceof ObjectNode items) {
            walk(items, path + "[]", visitor);
        }
    }
}
