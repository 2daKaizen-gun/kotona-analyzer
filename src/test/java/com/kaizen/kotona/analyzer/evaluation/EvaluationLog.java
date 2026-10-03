package com.kaizen.kotona.analyzer.evaluation;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 모델에게 물어 본 것을 적어 두고 다음 날 이어서 묻는다.
 *
 * <p>무료 티어로는 하루에 한 번에 다 물을 수 없다. 24 문장과 7 쌍이면 38 회인데,
 * 실제로 돌려 보니 그날 받을 수 있는 답은 열 몇 건이었다 — 순서 평가가 먼저 돌면서
 * 쿼터를 다 쓰고, 리스크 평가는 23 건이 429 로 끝났다.
 *
 * <p>그래서 답을 파일에 남긴다. 이미 답이 있는 문장은 다시 묻지 않으므로, 며칠에 걸쳐
 * 전체가 채워진다. 파일을 저장소에 함께 두는 이유는 이것이 측정 결과이기 때문이다 —
 * "모델이 이 문장에 이렇게 답했다" 는 기록이 없으면 일치율은 다시 주장일 뿐이다.
 *
 * <p>프롬프트나 모델을 바꾸면 이전 답은 더 이상 그 설정의 결과가 아니다.
 * {@code model} 과 {@code promptVersion} 이 함께 적히는 이유이고, 둘 중 하나가 바뀌면
 * 해당 항목은 다시 묻는다.
 */
final class EvaluationLog {

    /** 프롬프트를 의미 있게 바꿀 때 올린다. 올리면 기록된 답이 무효가 되어 다시 묻는다. */
    static final String PROMPT_VERSION = "2026-10-02-risk-definition";

    private static final Path FILE = Path.of("src/test/resources/evaluation/model-answers.json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();

    record Answer(String id, String kind, String model, String promptVersion, String askedOn, String value) {
    }

    private final Map<String, Answer> answers = new LinkedHashMap<>();

    static EvaluationLog load() {
        EvaluationLog log = new EvaluationLog();
        if (Files.exists(FILE)) {
            try {
                for (Answer a : MAPPER.readValue(Files.readString(FILE),
                        new tools.jackson.core.type.TypeReference<java.util.List<Answer>>() {})) {
                    log.answers.put(key(a.kind(), a.id()), a);
                }
            } catch (Exception e) {
                throw new IllegalStateException("기록을 읽지 못했다: " + FILE, e);
            }
        }
        return log;
    }

    /** 이 설정으로 이미 받은 답. 설정이 다르면 없는 것으로 본다. */
    String recorded(String kind, String id, String model) {
        Answer a = answers.get(key(kind, id));
        if (a == null || !a.model().equals(model) || !a.promptVersion().equals(PROMPT_VERSION)) {
            return null;
        }
        return a.value();
    }

    void record(String kind, String id, String model, String value) {
        answers.put(key(kind, id),
                new Answer(id, kind, model, PROMPT_VERSION, LocalDate.now().toString(), value));
    }

    void save() {
        try {
            Files.writeString(FILE, MAPPER.writeValueAsString(answers.values()) + "\n");
        } catch (Exception e) {
            throw new IllegalStateException("기록을 쓰지 못했다: " + FILE, e);
        }
    }

    private static String key(String kind, String id) {
        return kind + ":" + id;
    }
}
