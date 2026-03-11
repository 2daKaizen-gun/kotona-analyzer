package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.service.BusinessPhraseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/phrases")
@RequiredArgsConstructor
// Swagger 그룹화
@Tag(name = "Business Phrase", description = "비즈니스 일본어 숙어 라이브러리 API")
public class BusinessPhraseController {

    private final BusinessPhraseService service;

    @Operation(summary = "모든 숙어 조회", description = "DB에 저장된 모든 비즈니스 숙어를 정중도 순으로 조회")
    @GetMapping
    public List<BusinessPhrase> getAllPhrases() {
        return service.getAllPhrases();
    }

    @Operation(summary = "상황별 숙어 검색", description = "EMAIL, MEETING 등 특정 상황에 맞는 숙어만 필터링")
    @GetMapping("/search")
    public List<BusinessPhrase> getPhrasesBySituation(
            @Parameter(description = "검색할 상황 태그(예: EMAIL, MEETING)") @RequestParam String situation) {
        return service.getPhrasesBySituation(situation);
    }
}