package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.time.LocalDateTime;

/**
 * 이력 목록에 싣는 요약. 전체 분석 결과(fullAnalysisJson)는 빠져 있다.
 *
 * <p>목록은 한 화면에 여러 건이 실리는데, 저장된 분석 결과는 한 건당 수 KB 다.
 * 목록에 그걸 함께 보내면 대부분 펼쳐 보지도 않을 데이터를 매번 나른다.
 * 펼칠 때 GET /api/history/{id} 로 한 건만 가져온다.
 */
public record AnalysisHistorySummaryDTO(
        @JsonPropertyDescription("이력 id")
        Long id,

        @JsonPropertyDescription("분석한 원문")
        String userInput,

        @JsonPropertyDescription("총점 0~100")
        int totalScore,

        @JsonPropertyDescription("커뮤니케이션 분류")
        String category,

        @JsonPropertyDescription("위험 등급 SAFE / CAUTION / DANGER")
        String riskLevel,

        @JsonPropertyDescription("분석 일시")
        LocalDateTime createdAt
) {}
