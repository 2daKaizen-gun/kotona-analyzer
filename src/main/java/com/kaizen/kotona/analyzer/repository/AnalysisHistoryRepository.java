package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 커서 다음의 요약. {@code before} 보다 오래된(id 가 작은) 행만 본다.
     *
     * <p>offset 으로 넘기면 앞쪽 행이 지워지거나 새로 생길 때 경계가 밀린다 — 한 건을 지우고
     * "더 보기" 를 누르면 한 건을 건너뛰었다. 마지막으로 본 id 를 기준으로 하면 앞쪽의 변화와 무관하다.
     */
    @Query("""
            select new com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO(
                h.id, h.userInput, h.totalScore, h.category, h.riskLevel, h.createdAt)
            from AnalysisHistory h
            where h.id < :before
            """)
    Page<AnalysisHistorySummaryDTO> findSummariesBefore(@Param("before") long before, Pageable pageable);
}
