package com.kaizen.kotona.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.ThinkingConfig;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.utils.JapaneseTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final Client genAiClient;
    private final Schema nuanceResponseSchema;
    private final ObjectMapper objectMapper;

    // 형태소 분석 및 검증 서비스
    private final JapaneseTokenService tokenService;
    private final AnalysisValidator analysisValidator;
    private final AnalysisHistoryService historyService; // 저장 로직 연결

    @Value("${gemini.model:gemini-3.6-flash}")
    private String model;

    @Value("${gemini.max-output-tokens:8000}")
    private int maxOutputTokens;

    @Value("${gemini.temperature:0.7}")
    private float temperature;

    /** low | high. 비우면 모델 기본값을 쓴다. 낮출수록 응답이 빠르다. */
    @Value("${gemini.thinking-level:}")
    private String thinkingLevel;

    /**
     * 시스템 지시(고정). 매 요청 동일하므로 사용자 입력과 분리한다.
     * 출력 JSON 스키마는 responseSchema 가 강제하므로 여기에 중복 기술하지 않는다.
     */
    private static final String SYSTEM_PROMPT = """
            # Role
            You are a "Business Japanese Communication Expert" with 20 years of experience.
            You are specialized in detecting "Soft Rejection" signals and "Situational Context" in Japanese IT business.

            # Task
            1. Analyze the "KOTONA Nuance Score" (100 pts scale).
            2. Extract hidden "Honne" (True intent).
            3. Perform "Risk Detection" for business failure.
            4. Classify the "Communication Category" based on tone and format.
            5. Generate "Smart Replies" that provide strategic ways to respond to the detected situation.

            # Categories to Classify
            - EMAIL: Formal business emails (Standard greetings/signatures).
            - INTERVIEW: Job interviews or formal self-introductions.
            - MEETING: Real-time professional discussions or presentations.
            - INTERNAL_CHAT: Quick messaging via Slack/Teams (Polite but concise).
            - CASUAL: Professional but non-business daily interactions.

            # Scoring Criteria (Total 100 pts)
            1. Politeness (40 pts): Keigo accuracy.
            2. Indirectness (30 pts): "Aimaigo" (Vagueness) level.
            3. Etiquette (30 pts): Cushion phrases usage.

            # Risk Detection Guide (Red Flags)
            - Analyze phrases like "難しい", "検討", "確認" as potential "Soft-Rejections."
            - Evaluate risk levels (SAFE/CAUTION/DANGER) based on how much the speaker avoids a clear commitment.
            - Weigh the relationship context: EXTERNAL raises the stakes, INTERVIEW makes them critical.

            # Smart Reply Generation Strategy
            Generate 3 different response options based on the following scenarios:
            1. Clarification: A response to confirm the "Honne" (Ask for specific deadlines/details).
            2. Soft Acceptance: A polite way to accept even if the risk is CAUTION.
            3. Counter-proposal: A strategic way to propose an alternative when DANGER (rejection) is detected.

            # Constraints
            - Explanations, feedback, and strategy fields must be written in Korean.
            - "suggestions[].text" and "smartReplies[].content" must be written in Japanese.
            """;

    public NuanceResponseDTO analyzeJapaneseNuance(String userInput, String relationshipType) {
        // 입력값 정규화
        String cleanInput = JapaneseTextNormalizer.normalize(userInput);

        // 유효하지 않은 입력이면 즉시 컷함(불필요한 API 호출 방지)
        if (!JapaneseTextNormalizer.isValid(cleanInput)) {
            throw new IllegalArgumentException("분석할 수 없는 문장입니다. 올바른 일본어를 입력하세요.");
        }

        // 형태소 분석을 통한 정중어 사전 체크(Token Analysis)
        boolean hasPoliteTokens = tokenService.hasPoliteEnding(cleanInput);

        var configBuilder = GenerateContentConfig.builder()
                .systemInstruction(Content.fromParts(Part.fromText(SYSTEM_PROMPT)))
                .temperature(temperature)
                .maxOutputTokens(maxOutputTokens)
                // 스키마를 API 레벨에서 강제 → 마크다운 펜스 제거 같은 방어 코드가 불필요해진다.
                .responseMimeType("application/json")
                .responseSchema(nuanceResponseSchema);

        if (thinkingLevel != null && !thinkingLevel.isBlank()) {
            configBuilder = configBuilder.thinkingConfig(
                    ThinkingConfig.builder().thinkingLevel(thinkingLevel).build());
        }

        GenerateContentConfig config = configBuilder.build();

        try {
            GenerateContentResponse response = genAiClient.models.generateContent(
                    model, buildUserMessage(cleanInput, relationshipType), config);

            String rawText = response.text();
            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("AI가 응답을 생성하지 못하였습니다.");
            }

            NuanceResponseDTO aiResult = objectMapper.readValue(rawText, NuanceResponseDTO.class);

            // 하이브리드 검증 (Validator 호출)
            NuanceResponseDTO validatedResult =
                    analysisValidator.validate(aiResult, cleanInput, relationshipType, hasPoliteTokens);

            // DB에 저장
            historyService.saveHistory(cleanInput, validatedResult);

            return validatedResult;

        } catch (IllegalArgumentException | ApiException e) {
            // 입력 검증 실패 → 400, Gemini API 오류 → 429/502.
            // GlobalExceptionHandler 가 상태코드로 매핑하므로 여기서 감싸지 않는다.
            // (감싸면 업스트림 원문이 500 응답 본문으로 그대로 새어 나간다.)
            throw e;
        } catch (Exception e) {
            log.error("Gemini 분석 중 오류 발생", e);
            throw new RuntimeException("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /** 사용자 입력과 관계 맥락만 담는다. 고정 지시는 시스템 지시가 담당한다. */
    private String buildUserMessage(String cleanInput, String relationshipType) {
        return """
                # Relationship Context: %s
                (INTERNAL = 사내, EXTERNAL = 사외/고객사, INTERVIEW = 면접)

                # User Input
                %s
                """.formatted(relationshipType, cleanInput);
    }
}
