package com.kaizen.kotona.analyzer.repository;

import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BusinessPhraseRepository extends JpaRepository<BusinessPhrase, Long> {

    // 상황 태그로 숙어 목록 필터링 기능
    Page<BusinessPhrase> findBySituation(Situation situation, Pageable pageable);

    // 정렬은 Pageable 이 들고 온다. 메서드 이름에 박으면 Pageable 의 Sort 와 충돌한다.
    Page<BusinessPhrase> findAllBy(Pageable pageable);

    /*
     * 커서 다음 페이지. 정렬은 (정중도 내림차순, 정중도 없는 행은 맨 뒤, id 오름차순)이고,
     * 커서는 화면이 마지막으로 받은 행의 (정중도, id) 다. offset 으로 넘기면 앞쪽 행이 지워지거나
     * 끼어들 때 경계가 밀려 한 건을 건너뛰거나 두 번 보인다 — 이력이 그랬고(#91), 사전도 같다.
     *
     * 정중도가 비어 있는 행 때문에 둘로 나뉜다. 커서가 정중도 있는 행이면 "더 낮은 정중도, 같은
     * 정중도의 더 큰 id, 그리고 정중도 없는 행 전부" 가 뒤에 오고, 커서가 이미 정중도 없는 구간이면
     * "정중도 없는 행 중 더 큰 id" 만 남는다.
     */
    String AFTER_RATED = """
            (p.politenessLevel < :level
                or (p.politenessLevel = :level and p.id > :id)
                or p.politenessLevel is null)
            """;
    String AFTER_UNRATED = "(p.politenessLevel is null and p.id > :id)";

    @Query("select p from BusinessPhrase p where " + AFTER_RATED)
    Page<BusinessPhrase> findAfter(@Param("level") int level, @Param("id") long id, Pageable pageable);

    @Query("select p from BusinessPhrase p where " + AFTER_UNRATED)
    Page<BusinessPhrase> findAfterUnrated(@Param("id") long id, Pageable pageable);

    @Query("select p from BusinessPhrase p where p.situation = :situation and " + AFTER_RATED)
    Page<BusinessPhrase> findBySituationAfter(@Param("situation") Situation situation,
                                              @Param("level") int level, @Param("id") long id, Pageable pageable);

    @Query("select p from BusinessPhrase p where p.situation = :situation and " + AFTER_UNRATED)
    Page<BusinessPhrase> findBySituationAfterUnrated(@Param("situation") Situation situation,
                                                     @Param("id") long id, Pageable pageable);

    // 등록 시 중복 확인
    boolean existsByPhrase(String phrase);

    // 수정 시 중복 확인 — 자기 자신은 제외해야 표현을 그대로 둔 채 뜻만 고칠 수 있다
    boolean existsByPhraseAndIdNot(String phrase, Long id);
}
