package com.kaizen.kotona.controller;

import com.kaizen.kotona.service.AnalysisHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class AnalysisHistoryController {

    private final AnalysisHistoryService historyService;
}
