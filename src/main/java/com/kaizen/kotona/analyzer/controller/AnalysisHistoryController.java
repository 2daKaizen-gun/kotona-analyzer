package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class AnalysisHistoryController {

    private final AnalysisHistoryService historyService;

    // 분석 이력 전체 목록 조회
    @GetMapping
    public List<AnalysisHistory> getAllHistory() {
        return historyService.getHistoryList();
    }

    // 특정 이력 삭제
    @DeleteMapping("/{id}")
    public void deleteHistory(@PathVariable Long id) {
        historyService.deleteHistory(id);
    }
}
