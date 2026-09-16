package com.kaizen.kotona.analyzer.client;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 실제 Gemini 호출. 이 클래스 외에는 SDK 의 Client 를 직접 만지지 않는다. */
@Component
@RequiredArgsConstructor
public class GenAiNuanceModelClient implements NuanceModelClient {

    private final Client genAiClient;

    @Override
    public String generate(String model, String prompt, GenerateContentConfig config) {
        return genAiClient.models.generateContent(model, prompt, config).text();
    }
}
