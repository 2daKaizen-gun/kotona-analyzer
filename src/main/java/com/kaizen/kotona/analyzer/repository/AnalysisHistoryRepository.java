package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {

    /**
     * 목록용 요약만 뽑는다. fullAnalysisJson(LONGTEXT)을 조회에서 아예 빼기 위해
     * 파생 쿼리 대신 생성자 표현식을 쓴다 — 엔티티로 받으면 그 컬럼까지 따라온다.
     */
    @Query("""
            select new com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO(
                h.id, h.userInput, h.totalScore, h.category, h.riskLevel, h.createdAt)
            from AnalysisHistory h
            """)
    Page<AnalysisHistorySummaryDTO> findSummaries(Pageable pageable);
}
