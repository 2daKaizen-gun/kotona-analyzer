package com.kotona.analyzer;

import com.kaizen.kotona.repository.BusinessPhraseRepository;
import com.kaizen.kotona.service.BusinessPhraseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    }
}
