package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.dto.PageResponse;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.service.AnalysisHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
@Tag(name = "Analysis History", description = "분석 이력 조회 및 삭제 API")
public class AnalysisHistoryController {

    private final AnalysisHistoryService historyService;

    @Operation(summary = "분석 이력 목록",
            description = "최신순 요약 목록. 저장된 분석 결과 전체는 포함되지 않는다 — 한 건을 펼칠 때 상세 조회를 쓴다.")
    @GetMapping
    public PageResponse<AnalysisHistorySummaryDTO> getHistory(
            @Parameter(description = "0부터 시작하는 페이지 번호") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (최대 100)") @RequestParam(defaultValue = "20") int size) {
        return historyService.getHistoryPage(page, size);
    }

    @Operation(summary = "분석 이력 상세", description = "저장된 분석 결과 전체(fullAnalysisJson)를 포함한 한 건.")
    @GetMapping("/{id}")
    public AnalysisHistory getHistoryDetail(@PathVariable Long id) {
        return historyService.getHistory(id);
    }

    // 사전 삭제와 같은 204 로 답한다. 같은 동작에 다른 코드를 주면 호출하는 쪽이
    // 엔드포인트마다 다르게 처리해야 하고, 그 차이에 이유가 없다.
    @Operation(summary = "분석 이력 삭제")
    // 반환 타입만 보면 springdoc 은 200 이라고 적는다. 실제로 나가는 코드를 밝힌다.
    @ApiResponse(responseCode = "204", description = "삭제됨", content = @Content)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHistory(@PathVariable Long id) {
        historyService.deleteHistory(id);
        return ResponseEntity.noContent().build();
    }
}
