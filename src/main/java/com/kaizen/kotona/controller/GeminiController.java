package com.kaizen.kotona.controller;

import com.kaizen.kotona.service.GeminiService;
import com.kaizen.kotona.dto.NuanceResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GeminiController {

    private final GeminiService geminiService;

    // 반환 타입을 String에서 NuanceResponseDTO로 변경
    @GetMapping("/analyze")
    public NuanceResponseDTO analyze(@RequestParam(name = "text") String text) {
        // 서비스가 DTO를 반환하므로 바로 return이 가능
        return geminiService.analyzeJapaneseNuance(text);
    }
}