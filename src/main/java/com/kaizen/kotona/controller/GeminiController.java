package com.kaizen.kotona.controller;

import com.kaizen.kotona.service.GeminiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;

    // 테스트 URL: http://localhost:8081/api/v1/analyze?text=お疲れ
    @GetMapping("/api/v1/analyze")
    public String analyze(@RequestParam String text) {
        return geminiService.analyzeJapaneseNuance(text);
    }
}