package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {
    // 전체 이력 조회
    List<AnalysisHistory> findByOrderByCreatedAtDesc();
}
