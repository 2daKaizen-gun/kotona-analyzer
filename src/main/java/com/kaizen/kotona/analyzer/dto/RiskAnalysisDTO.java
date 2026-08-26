package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record RiskAnalysisDTO(
    @JsonPropertyDescription("위험 등급. \"SAFE\", \"CAUTION\", \"DANGER\" 중 하나.")
    String riskLevel,

    @JsonPropertyDescription("감지된 위험 신호(소프트 리젝션 등) 목록. 한국어로 작성한다.")
    List<String> redFlags,

    @JsonPropertyDescription("권장 비즈니스 대응 전략. 한국어로 작성한다.")
    String copingStrategy
) {}
