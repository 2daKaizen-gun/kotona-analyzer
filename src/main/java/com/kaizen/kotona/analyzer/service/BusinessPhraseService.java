package com.kaizen.kotona.analyzer.service;

import com.kaizen.kotona.analyzer.dto.PhraseRequestDTO;
import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import com.kaizen.kotona.analyzer.exception.DuplicatePhraseException;
import com.kaizen.kotona.analyzer.exception.PhraseNotFoundException;
import com.kaizen.kotona.analyzer.repository.BusinessPhraseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 기본은 조회 전용. 쓰기 메서드는 각자 @Transactional 로 연다.
public class BusinessPhraseService {

    private final BusinessPhraseRepository repository;

    // 모든 숙어 가져오기
    public List<BusinessPhrase> getAllPhrases() {
        return repository.findAllByOrderByPolitenessLevelDesc();
    }

    // 특정 상황 (ex: 면접) 숙어만 가져오기
    public List<BusinessPhrase> getPhrasesBySituation(Situation situation) {
        return repository.findBySituation(situation);
    }

    /**
     * 표현을 새로 등록한다.
     *
     * <p>중복은 저장 전에 직접 확인한다. UNIQUE 제약에만 맡기면 DataIntegrityViolationException 이
     * 올라오는데, 그 예외는 중복뿐 아니라 다른 제약 위반에도 똑같이 던져져 원인을 가려낼 수 없다.
     * saveAndFlush 로 즉시 반영해, 확인과 저장 사이에 같은 표현이 끼어든 경우에도
     * 위반이 커밋 시점이 아니라 이 메서드 안에서 드러나게 한다.
     */
    @Transactional
    public BusinessPhrase create(PhraseRequestDTO request) {
        String phrase = request.phrase().strip();
        if (repository.existsByPhrase(phrase)) {
            throw new DuplicatePhraseException(phrase);
        }
        return repository.saveAndFlush(BusinessPhrase.create(
                phrase,
                request.meaning().strip(),
                request.situation(),
                request.politenessLevel(),
                request.usageExample()));
    }

    /** 표현을 통째로 교체한다(PUT). */
    @Transactional
    public BusinessPhrase update(Long id, PhraseRequestDTO request) {
        BusinessPhrase target = repository.findById(id)
                .orElseThrow(() -> new PhraseNotFoundException(id));

        String phrase = request.phrase().strip();
        if (repository.existsByPhraseAndIdNot(phrase, id)) {
            throw new DuplicatePhraseException(phrase);
        }

        target.replace(
                phrase,
                request.meaning().strip(),
                request.situation(),
                request.politenessLevel(),
                request.usageExample());
        repository.flush(); // create 와 같은 이유로 위반을 여기서 드러낸다
        return target;
    }

    /**
     * 표현을 삭제한다.
     *
     * <p>Spring Data 3 의 deleteById 는 없는 id 에도 조용히 통과하므로, 404 를 주려면 먼저 확인해야 한다.
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new PhraseNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
