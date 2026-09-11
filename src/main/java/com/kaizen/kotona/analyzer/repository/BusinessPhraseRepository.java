package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BusinessPhraseRepository extends JpaRepository<BusinessPhrase, Long> {

    // 상황 태그로 숙어 목록 필터링 기능
    List<BusinessPhrase> findBySituation(Situation situation);

    // 정중도 높은 순서대로 전체 목록 가져오는 기능
    List<BusinessPhrase> findAllByOrderByPolitenessLevelDesc();

    // 등록 시 중복 확인
    boolean existsByPhrase(String phrase);

    // 수정 시 중복 확인 — 자기 자신은 제외해야 표현을 그대로 둔 채 뜻만 고칠 수 있다
    boolean existsByPhraseAndIdNot(String phrase, Long id);
}
