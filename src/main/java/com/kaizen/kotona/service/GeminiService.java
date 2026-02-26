package com.kaizen.kotona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import com.kaizen.kotona.dto.NuanceResponseDTO;
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

    public NuanceResponseDTO analyzeJapaneseNuance(String userInput) {
        // PROMPT_DESIGN.md 기반 마스터 프롬프트
        String prompt = String.format("""
            # Role
            You are a "Business Japanese Communication Expert" with 20 years of experience.
            
            # Task
            Analyze the following text based on Japanese business etiquette.
            
            # User Input: "%s"
            
            # Constraints
            - Respond ONLY in valid JSON format.
            - Follow the structure defined in PROMPT_DESIGN.md.
            
            # Output JSON Schema
            {
                "score": 1-10,
                "evaluation": { "summary": "string", "keigo_check": boolean, "cushion_phrase_check": boolean },
                "feedback": { "issues": ["string"], "cultural_nuance": "string" },
                "suggestions": [{ "text": "string", "level": "standard/highest" }]
            }
            """, userInput);

        try {
            // AI에게 요청을 보내고 응답을 받음
            var response = generativeModel.generateContent(prompt);

            // Empty Response check
            String rawText = ResponseHandler.getText(response);
            if (rawText == null || rawText.isBlank()) {
                throw new RuntimeException("AI가 응답 생성하지 못하였습니다.");
            }

            return parseJson(rawText);

        } catch (Exception e) {
            // 구글 세이프티 필터 등에 걸렸을 경우의 에러 메시지 처리
            if (e.getMessage().contains("Safety")) {
                throw new RuntimeException("입력 내용이 부적절하여 분석이 차단되었습니다.");
            }

            throw new RuntimeException("분석 중 오류가 발생했습니다: " + e.getMessage());
        }
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