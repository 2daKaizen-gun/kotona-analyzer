package com.kaizen.kotona.analyzer.dto;

public record HonneDTO(
        String tatemae, // 표면적 의미
        String trueIntent, // 본심
        String actionItem // 사용자 행동 가이드
) {}
