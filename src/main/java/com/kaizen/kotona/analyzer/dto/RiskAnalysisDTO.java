package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public record RiskAnalysisDTO(
    @JsonPropertyDescription("확답을 피하는 정도. \"SAFE\"(확답·수락·요청·다음 단계 명시), \"CAUTION\"(기한 없는 보류), \"DANGER\"(완곡한 거절) 중 하나. 무례함이나 정중도와는 무관하다 — 반말이어도 거절이 아니면 SAFE 다.")
    String riskLevel,

    @JsonPropertyDescription("감지된 위험 신호(소프트 리젝션 등) 목록. 한국어로 작성한다.")
    List<String> redFlags,

    @JsonPropertyDescription("권장 비즈니스 대응 전략. 한국어로 작성한다.")
    String copingStrategy
) {}
