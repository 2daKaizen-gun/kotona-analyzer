package com.kaizen.kotona.analyzer;

import static org.mockito.BDDMockito.*;
import static org.assertj.core.api.Assertions.*;

import com.kaizen.kotona.analyzer.dto.PhraseRequestDTO;
import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import com.kaizen.kotona.analyzer.exception.DuplicatePhraseException;
import com.kaizen.kotona.analyzer.exception.PhraseNotFoundException;
import com.kaizen.kotona.analyzer.repository.BusinessPhraseRepository;
import com.kaizen.kotona.analyzer.service.BusinessPhraseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

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
        BusinessPhrase phrase1 = new BusinessPhrase(1L, "承知いたしました", "알겠습니다", Situation.EMAIL, 5, "예시1");
        BusinessPhrase phrase2 = new BusinessPhrase(2L, "念のため", "만약을 위해", Situation.CONFIRMATION, 2, "예시2");
        given(repository.findAllByOrderByPolitenessLevelDesc()).willReturn(List.of(phrase1, phrase2));

        // 테스트할 메서드 실행
        List<BusinessPhrase> result = service.getAllPhrases();

        // AssertJ 사용한 결과 검증
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPolitenessLevel()).isEqualTo(5);
        // 메서드 호출 횟수 검증
        verify(repository, times(1)).findAllByOrderByPolitenessLevelDesc();
    }

    @Test
    @DisplayName("특정 상황(EMAIL)으로 검색 시 해당 데이터만 반환되어야 함.")
    void getPhrasesBySituationTest() {
        // given
        BusinessPhrase phrase = new BusinessPhrase(1L, "承知いたしました", "알겠습니다", Situation.EMAIL, 5, "예시1");
        given(repository.findBySituation(Situation.EMAIL)).willReturn(List.of(phrase));

        // when
        List<BusinessPhrase> result = service.getPhrasesBySituation(Situation.EMAIL);

        // then
        assertThat(result).allMatch(p -> p.getSituation() == Situation.EMAIL);
    }

    @Test
    @DisplayName("등록 시 앞뒤 공백을 걷어낸 표현으로 중복을 확인하고 저장한다.")
    void createStripsWhitespaceAndSaves() {
        given(repository.existsByPhrase("承知いたしました")).willReturn(false);
        given(repository.saveAndFlush(any(BusinessPhrase.class))).willAnswer(inv -> inv.getArgument(0));

        BusinessPhrase saved = service.create(
                new PhraseRequestDTO("  承知いたしました ", " 알겠습니다 ", Situation.EMAIL, 5, "예시"));

        // 공백만 다른 표현이 별개 행으로 쌓이지 않도록 저장 전에 정리한다
        assertThat(saved.getPhrase()).isEqualTo("承知いたしました");
        assertThat(saved.getMeaning()).isEqualTo("알겠습니다");
        assertThat(saved.getSituation()).isEqualTo(Situation.EMAIL);
    }

    @Test
    @DisplayName("이미 있는 표현을 등록하면 저장하지 않고 DuplicatePhraseException 을 던진다.")
    void createRejectsDuplicate() {
        given(repository.existsByPhrase("承知いたしました")).willReturn(true);

        assertThatThrownBy(() -> service.create(request("承知いたしました")))
                .isInstanceOf(DuplicatePhraseException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("수정은 id 를 제외한 모든 필드를 교체한다.")
    void updateReplacesAllFields() {
        BusinessPhrase existing = new BusinessPhrase(1L, "念のため", "만약을 위해", Situation.CONFIRMATION, 2, "옛 예시");
        given(repository.findById(1L)).willReturn(Optional.of(existing));
        given(repository.existsByPhraseAndIdNot("念のため", 1L)).willReturn(false);

        BusinessPhrase updated = service.update(1L,
                new PhraseRequestDTO("念のため", "혹시 몰라서", Situation.EMAIL, 3, null));

        assertThat(updated.getId()).isEqualTo(1L);
        assertThat(updated.getMeaning()).isEqualTo("혹시 몰라서");
        assertThat(updated.getSituation()).isEqualTo(Situation.EMAIL);
        assertThat(updated.getPolitenessLevel()).isEqualTo(3);
        // PUT 은 전체 교체 — 보내지 않은 필드는 비워진다
        assertThat(updated.getUsageExample()).isNull();
        verify(repository).flush();
    }

    @Test
    @DisplayName("수정하려는 표현이 다른 행과 겹치면 DuplicatePhraseException 을 던진다.")
    void updateRejectsCollisionWithAnotherRow() {
        given(repository.findById(1L)).willReturn(Optional.of(
                new BusinessPhrase(1L, "念のため", "만약을 위해", Situation.CONFIRMATION, 2, null)));
        given(repository.existsByPhraseAndIdNot("承知いたしました", 1L)).willReturn(true);

        assertThatThrownBy(() -> service.update(1L, request("承知いたしました")))
                .isInstanceOf(DuplicatePhraseException.class);
    }

    @Test
    @DisplayName("없는 id 를 수정하면 PhraseNotFoundException 을 던진다.")
    void updateMissingIdThrows() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(99L, request("承知いたしました")))
                .isInstanceOf(PhraseNotFoundException.class);
    }

    @Test
    @DisplayName("없는 id 를 삭제하면 조용히 넘어가지 않고 PhraseNotFoundException 을 던진다.")
    void deleteMissingIdThrows() {
        given(repository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(PhraseNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    @DisplayName("있는 id 는 삭제한다.")
    void deleteExisting() {
        given(repository.existsById(1L)).willReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    private static PhraseRequestDTO request(String phrase) {
        return new PhraseRequestDTO(phrase, "알겠습니다", Situation.EMAIL, 5, "예시");
    }
}
