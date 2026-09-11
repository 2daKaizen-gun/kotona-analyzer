package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.dto.PhraseRequestDTO;
import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import com.kaizen.kotona.analyzer.service.BusinessPhraseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "상황별 숙어 검색", description = "EMAIL, MEETING 등 특정 상황에 맞는 숙어만 필터링. 허용되지 않은 값이면 400")
    @GetMapping("/search")
    public List<BusinessPhrase> getPhrasesBySituation(
            @Parameter(description = "검색할 상황 태그(예: EMAIL, MEETING)") @RequestParam Situation situation) {
        return service.getPhrasesBySituation(situation);
    }

    @Operation(summary = "숙어 추가", description = "새 표현을 사전에 등록. 이미 있는 표현이면 409")
    @PostMapping
    public ResponseEntity<BusinessPhrase> createPhrase(@Valid @RequestBody PhraseRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @Operation(summary = "숙어 수정", description = "표현을 통째로 교체. 없는 id 면 404, 다른 행과 표현이 겹치면 409")
    @PutMapping("/{id}")
    public BusinessPhrase updatePhrase(@PathVariable Long id, @Valid @RequestBody PhraseRequestDTO request) {
        return service.update(id, request);
    }

    @Operation(summary = "숙어 삭제",
            description = "없는 id 면 404. 기본 사전(data.sql 시드)의 표현은 삭제해도 다음 부팅 때 다시 들어간다")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePhrase(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
