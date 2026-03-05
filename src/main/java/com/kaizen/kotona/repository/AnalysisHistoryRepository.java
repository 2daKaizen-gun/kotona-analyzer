package com.kaizen.kotona.repository;

import com.kaizen.kotona.entity.AnalysisHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {
    // 최신순으로 전체 이력 조회
    List<AnalysisHistory> findAllByCreatedAtDesc();
}
