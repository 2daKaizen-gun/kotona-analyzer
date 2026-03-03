package com.kaizen.kotona.dto;

import java.util.List;

public record RiskAnalysisDTO(
    String riskLevel, // SAFE, CAUTION, DANGER
    List<String> redFlags, // 감지된 위험 신호
    String copingStrategy // 대응 전략 가이드
) {}
