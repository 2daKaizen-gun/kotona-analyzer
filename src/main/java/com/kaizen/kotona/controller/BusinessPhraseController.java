package com.kaizen.kotona.controller;

import com.kaizen.kotona.entity.BusinessPhrase;
import com.kaizen.kotona.service.BusinessPhraseService;
import io.swagger.v3.oas.annotations.Operation;
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

    // 모든 숙어 목록 조회 (GET http://localhost:8081/api/phrases)
    @Operation(summary = "모든 숙어 조회", description = "DB에 저장된 모든 비즈니스 숙어를 정중도 순으로 조회")
    @GetMapping
    public List<BusinessPhrase> getAllPhrases() {
        return service.getAllPhrases();
    }

    // 상황별 숙어 필터링 조회 (GET http://localhost:8081/api/phrases/search?situation=EMAIL)
    @GetMapping("/search")
    public List<BusinessPhrase> getPhrasesBySituation(@RequestParam String situation) {
        return service.getPhrasesBySituation(situation);
    }
}