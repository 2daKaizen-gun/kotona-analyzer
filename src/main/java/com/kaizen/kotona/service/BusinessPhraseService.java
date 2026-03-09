package com.kaizen.kotona.service;

import com.kaizen.kotona.entity.BusinessPhrase;
import com.kaizen.kotona.repository.BusinessPhraseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 조회 전용
public class BusinessPhraseService {

    private final BusinessPhraseRepository repository;

    // 모든 숙어 가져오기
    public List<BusinessPhrase> getAllPhrases() {
        return repository.findAllByOrderByPolitenessLevelDesc();
    }

    // 특정 상황 (ex: 면접) 숙어만 가져오기
    public List<BusinessPhrase> getPhrasesBySituation(String situation) {
        return repository.findBySituation(situation);
    }
}
