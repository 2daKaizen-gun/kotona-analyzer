package com.kaizen.kotona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.kaizen.kotona.dto.NuanceResponseDTO;
import com.kaizen.kotona.utils.JapaneseTextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiService {

    private final GenerativeModel generativeModel;
    private final ObjectMapper objectMapper; // JSON 파싱용

    // 형태소 분석 및 검증 서비스
    private final JapaneseTokenService tokenService;
    private final AnalysisValidator analysisValidator;
    private final AnalysisHistoryService historyService; // 저장 로직 연결

    public NuanceResponseDTO analyzeJapaneseNuance(String userInput, String relationshipType) {
        // 입력값 정규화
        String cleanInput = JapaneseTextNormalizer.normalize(userInput);

        // 유효하지 않은 입력이면 즉시 컷함(AI API 호출 방지)
        if (!JapaneseTextNormalizer.isValid(cleanInput)) {
            throw new IllegalArgumentException("분석할 수 없는 문장입니다. 올바른 일본어를 입력하세요.");
        }

        // 형태소 분석을 통한 정중어 사전 체크(Token Analysis)
        boolean hasPoliteTokens = tokenService.hasPoliteEnding(cleanInput);

        // PROMPT_DESIGN.md 기반 마스터 프롬프트
        String prompt = createPrompt(cleanInput);

        try {
            // AI에게 요청을 보내고 응답을 받음
            var response = generativeModel.generateContent(prompt);

            // Empty Response check
            String rawText = ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("AI가 응답 생성하지 못하였습니다.");
            }

            // JSON 파싱
            NuanceResponseDTO aiResult = parseJson(rawText);

            // 하이브리드 검증 (Validator 호출)
            NuanceResponseDTO validatedResult = analysisValidator.validate(aiResult, cleanInput, relationshipType, hasPoliteTokens);

            // DB에 저장
            historyService.saveHistory(cleanInput, validatedResult);

            return validatedResult;

        } catch (Exception e) {
            // 구글 세이프티 필터 등에 걸렸을 경우의 에러 메시지 처리
            if (e.getMessage().contains("Safety")) {
                throw new RuntimeException("입력 내용이 부적절하여 분석이 차단되었습니다.");
            }

            throw new RuntimeException("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private String createPrompt(String cleanInput) {
        return String.format("""
            # Role
            You are a "Business Japanese Communication Expert" with 20 years of experience.
            You are specialized in detecting "Soft Rejection" signals and "Situational Context" in Japanese IT business.
            
            # Task
            1. Analyze the "KOTONA Nuance Score" (100 pts scale).
            2. Extract hidden "Honne" (True intent).
            3. Perform "Risk Detection" for business failure.
            4. Classify the "Communication Category" based on tone and format.
            5. Generate "Smart Replies" that provide strategic ways to respond to the detected situation.
            
            # User Input: "%s"
            
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
            
            # Smart Reply Generation Strategy
            Generate 3 different response options based on the following scenarios:
            1. Clarification: A response to confirm the "Honne" (Ask for specific deadlines/details).
            2. Soft Acceptance: A polite way to accept even if the risk is CAUTION.
            3. Counter-proposal: A strategic way to propose an alternative when DANGER (rejection) is detected.

            # Output JSON Schema
            {
              "totalScore": 0-100,
              "category": "EMAIL/INTERVIEW/MEETING/INTERNAL_CHAT/CASUAL",
              "metrics": {
                "politeness": 0-40,
                "indirectness": 0-30,
                "etiquette": 0-30
              },
              "evaluation": {
                "summary": "string",
                "keigo_check": boolean,
                "cushion_phrase_check": boolean
              },
              "feedback": {
                "issues": ["string"],
                "cultural_nuance": "string"
              },
              "suggestions": [
                { "text": "string", "level": "standard/highest" }
              ],
              "sentiment": {
                  "polarity": "Positive/Neutral/Negative",
                  "confidence": 0.0-1.0,
                  "honne": {
                      "tatemae": "표면적 의미 (Korean)",
                      "trueIntent": "숨겨진 본심 (Korean)",
                      "actionItem": "권장 행동 (Korean)"
                  }
              },
              "riskAnalysis": {
                  "riskLevel": "SAFE/CAUTION/DANGER",
                  "redFlags": ["감지된 위험 신호 리스트 (Korean)"],
                  "copingStrategy": "비즈니스 전략 제안 (Korean)"
              },
              "smartReplies": [
                 {
                   "scenario": "Clarification / Soft Landing / Counter-proposal",
                   "content": "실제 사용할 수 있는 정중한 일본어 답장 문구",
                   "description": "이 답장의 의도와 기대 효과 (Korean)",
                   "nuanceLevel": "Standard / Soft / Firm"
                 }
              ]
            }
            
            # Constraints
            - Respond ONLY in valid JSON.
            - Explanations/Feedback must be in Korean.
            - Suggestions must be in Japanese.
            """, cleanInput);
    }

    private NuanceResponseDTO parseJson(String text) {
        try {
            // 마크다운 블록(```json) 제거 정규식
            Pattern pattern = Pattern.compile("(?s)```(?:json)?\\s*(.*?)\\s*```");
            Matcher matcher = pattern.matcher(text);
            String jsonContent = matcher.find() ? matcher.group(1).trim() : text.trim();

            return objectMapper.readValue(jsonContent, NuanceResponseDTO.class);
        } catch (Exception e) {
            // Malformed JSON 처리
            throw new RuntimeException("AI 응답 데이터 형식이 올바르지 않습니다.");
        }
    }
}