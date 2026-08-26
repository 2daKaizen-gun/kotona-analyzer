package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.dto.AnalyzeRequestDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.service.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Nuance Analysis", description = "일본어 비즈니스 문장 뉘앙스 분석 API")
public class AnalyzeController {

    private final GeminiService geminiService;

    // DB 저장 + 유료 AI 호출이 일어나는 '상태 변경' 요청이므로 GET이 아닌 POST 사용.
    // (GET은 프록시/브라우저가 캐싱·프리페치·로그에 남겨 부작용/비용 문제를 유발)
    @Operation(summary = "문장 뉘앙스 분석", description = "Gemini 로 본음/건전, 정중도 점수, 리스크, 추천 답장을 분석한다.")
    @PostMapping("/analyze")
    public NuanceResponseDTO analyze(@Valid @RequestBody AnalyzeRequestDTO request) {
        return geminiService.analyzeJapaneseNuance(
                request.text(),
                request.relationshipTypeOrDefault());
    }
}
