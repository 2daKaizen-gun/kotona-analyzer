package com.kaizen.kotona.entity;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String userInput; // 사용자가 입력한 일본어 문장

    private int totalScore; // 분석 총점
    private String category; // 분류된 카테고리(EMAIL, MEETING 등)
    private String riskLevel; // 리스트 등급(SAFE, CAUTION, DANGER)

    @Column(columnDefinition = "LONGTEXT")
    private String fullAnalysisJson; // 전체 분석 결과 DTO를 JSON 문자열로 저장
}
