package com.kaizen.kotona.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "analysis_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt; // 분석 일시

    @Builder
    public AnalysisHistory(String userInput, int totalScore, String category, String riskLevel, String fullAnalysisJson) {
        this.userInput = userInput;
        this.totalScore = totalScore;
        this.category = category;
        this.riskLevel = riskLevel;
        this.fullAnalysisJson = fullAnalysisJson;
    }
}