package com.kaizen.kotona.controller;

import com.kaizen.kotona.entity.AnalysisHistory;
import com.kaizen.kotona.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class AnalysisHistoryController {

    private final AnalysisHistoryService historyService;

    // 분석 이력 전체 목록 조회


    // 특정 이력 삭제


}
