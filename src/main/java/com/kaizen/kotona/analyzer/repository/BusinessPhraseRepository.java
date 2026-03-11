package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BusinessPhraseRepository extends JpaRepository<BusinessPhrase, Long> {

    // 상황 태그로 숙어 목록 필터링 기능
    List<BusinessPhrase> findBySituation(String situation);

    // 정중도 높은 순서대로 전체 목록 가져오는 기능
    List<BusinessPhrase> findAllByOrderByPolitenessLevelDesc();
}