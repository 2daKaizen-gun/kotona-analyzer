package com.kaizen.kotona.service;

import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GeminiService {

    private final GenerativeModel generativeModel;

    public String analyzeJapaneseNuance(String userInput) {
        // 페르소나
        String prompt = String.format("""
            당신은 일본 비즈니스 매너 및 경어(敬語) 전문가입니다. 
            다음 문장이 일본 IT 기업 환경에서 사용하기에 적절한지 분석해주세요.
            
            [분석할 문장]: %s
            
            [출력 양식]:
            1. 정중함 점수 (1~10점)
            2. 주요 경어 포인트 분석
            3. 상황별 적합성 (메일, 회의, 면접 등)
            4. 더 나은 비즈니스 표현 제안
            
            결과는 한국어로 작성해주세요.
            """, userInput);

        try {
            // AI에게 요청을 보내고 응답을 받음
            var response = generativeModel.generateContent(prompt);
            return ResponseHandler.getText(response);
        } catch (Exception e) {
            return "AI 분석 중 오류가 발생했습니다: " + e.getMessage();
        }
    }
}