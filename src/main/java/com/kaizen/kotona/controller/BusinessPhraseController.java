package com.kaizen.kotona.controller;

import com.kaizen.kotona.entity.BusinessPhrase;
import com.kaizen.kotona.service.BusinessPhraseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/phrases")
@RequiredArgsConstructor
public class BusinessPhraseController {

    private final BusinessPhraseService service;

    // 모든 숙어 목록 조회 (GET http://localhost:8081/api/phrases)
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