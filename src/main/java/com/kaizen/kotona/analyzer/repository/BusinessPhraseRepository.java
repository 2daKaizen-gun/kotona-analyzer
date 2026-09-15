package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusinessPhraseRepository extends JpaRepository<BusinessPhrase, Long> {

    // 상황 태그로 숙어 목록 필터링 기능
    Page<BusinessPhrase> findBySituation(Situation situation, Pageable pageable);

    // 정렬은 Pageable 이 들고 온다. 메서드 이름에 박으면 Pageable 의 Sort 와 충돌한다.
    Page<BusinessPhrase> findAllBy(Pageable pageable);

    // 등록 시 중복 확인
    boolean existsByPhrase(String phrase);

    // 수정 시 중복 확인 — 자기 자신은 제외해야 표현을 그대로 둔 채 뜻만 고칠 수 있다
    boolean existsByPhraseAndIdNot(String phrase, Long id);
}
