package com.kotona.analyzer;

import static org.mockito.BDDMockito.*;

import com.kaizen.kotona.entity.BusinessPhrase;
import com.kaizen.kotona.repository.BusinessPhraseRepository;
import com.kaizen.kotona.service.BusinessPhraseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

// Mockito 사용 설정
@ExtendWith(MockitoExtension.class)
class BusinessPhraseServiceTest {

    @Mock
    // 가짜 객체 생성
    private BusinessPhraseRepository repository;

    @InjectMocks
    // Mock 객체를 서비스에 주입
    private BusinessPhraseService service;

    @Test
    @DisplayName("전체 숙어 조회 시 정중도 내림차순으로 반환되어야 함.")
    void getAllPhrasesTest() {
        // given: 가짜 데이터와 행동 정의함
        BusinessPhrase phrase1 = new BusinessPhrase(1L, "承知いたしました", "알겠습니다", "EMAIL", 5, "예시1");
        BusinessPhrase phrase2 = new BusinessPhrase(2L, "念のため", "만약을 위해", "CONFIRMATION", 2, "예시2");
        given(repository.findAllByOrderByPolitenessLevelDesc()).willReturn(List.of(phrase1, phrase2));

        // 테스트할 메서드 실행
        List<BusinessPhrase> result = service.getAllPhrases();
    }
}
