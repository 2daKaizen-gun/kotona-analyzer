package com.kaizen.kotona.analyzer.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO record 트리에서 Gemini responseSchema 용 JSON Schema 를 생성한다.
 *
 * <p>프롬프트에 스키마를 손으로 적으면 DTO 와 어긋나는 문제가 생기므로(PROMPT_DESIGN.md 참고)
 * 스키마의 단일 원본은 항상 DTO 다. 필드 설명은 {@code @JsonPropertyDescription},
 * 필드명은 {@code @JsonProperty} 를 그대로 따른다.
 *
 * <p>Gemini 의 responseSchema 는 OpenAPI 3.0 스키마의 부분집합만 받으므로
 * {@code $schema}, {@code additionalProperties} 같은 키는 제거해야 한다.
 */
public final class NuanceSchemaFactory {

    private NuanceSchemaFactory() {
    }

    public static ObjectNode build(Class<?> type) {
        // 옵션 없이도 @JsonProperty 필드명과 @JsonPropertyDescription 설명을 그대로 반영한다.
        JacksonModule jacksonModule = new JacksonModule();

        SchemaGeneratorConfigBuilder configBuilder =
                new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                        .with(jacksonModule)
                        // $ref/definitions 를 쓰면 Gemini 가 거부하므로 전부 인라인한다.
                        .with(Option.INLINE_ALL_SCHEMAS);

        ObjectNode schema = new SchemaGenerator(configBuilder.build()).generateSchema(type);
        sanitize(schema);
        return schema;
    }

    /** Gemini 가 이해하지 못하는 키를 제거하고, 모든 객체 필드를 required 로 강제한다. */
    private static void sanitize(ObjectNode node) {
        node.remove("$schema");
        node.remove("additionalProperties");

        JsonNode properties = node.get("properties");
        if (properties instanceof ObjectNode props) {
            List<String> names = new ArrayList<>();
            props.fieldNames().forEachRemaining(names::add);

            // 필드가 누락되면 DTO 에 null 이 박히므로 전부 필수로 지정한다.
            ArrayNode required = node.putArray("required");
            names.forEach(required::add);

            for (String name : names) {
                if (props.get(name) instanceof ObjectNode child) {
                    sanitize(child);
                }
            }
        }

        if (node.get("items") instanceof ObjectNode items) {
            sanitize(items);
        }
    }
}
