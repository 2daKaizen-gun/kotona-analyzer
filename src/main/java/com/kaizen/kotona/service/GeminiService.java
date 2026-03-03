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

    public NuanceResponseDTO analyzeJapaneseNuance(String userInput) {
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

            // 하이브리드 검증 (Cross-Validation)
            // AI 결과와 직접 분석한 형태소 데이터 대조하여 최종 결과 반환
            return analysisValidator.validate(aiResult, cleanInput, hasPoliteTokens);

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
            You are specialized in detecting "Soft Rejection" signals in Japanese IT business contexts.
            
            # Task
            1. Analyze the "KOTONA Nuance Score" (100 pts scale).
            2. Extract hidden "Honne" (True intent).
            3. [NEW] Perform "Risk Detection" to identify potential business failure or rejection.
            
            # User Input: "%s"
            
            # Scoring Criteria (Total 100 pts)
            1. Politeness (40 pts): Keigo accuracy.
            2. Indirectness (30 pts): "Aimaigo" (Vagueness) level.
            3. Etiquette (30 pts): Cushion phrases usage.
            
            # Risk Detection Guide (Red Flags)
            - Detect if phrases like "難しい" (Difficult), "検討" (Consider), "確認します" (Will check) are used to avoid commitment.
            - Evaluate the "Risk Level" based on how vague the response is compared to the request.
            
            # Output JSON Schema
            {
              "totalScore": 0-100,
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
                  "copingStrategy": "위기 돌파를 위한 비즈니스 전략 제안 (Korean)"
              }
            }
            
            # Constraints
            - Respond ONLY in valid JSON.
            - All explanations/feedback must be in Korean.
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