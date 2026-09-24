package com.kaizen.kotona.analyzer.service;

import tools.jackson.databind.ObjectMapper;
import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.dto.PageResponse;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.exception.HistoryNotFoundException;
import com.kaizen.kotona.analyzer.repository.AnalysisHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.kaizen.kotona.analyzer.utils.Paging.MAX_PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AnalysisHistoryServiceTest {

    @Mock
    private AnalysisHistoryRepository repository;

    @SuppressWarnings("unused") // 저장 경로에서만 쓰이지만 생성자 주입에 필요하다
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AnalysisHistoryService service;

    @Test
    @DisplayName("목록은 최신순으로, 같은 시각이면 id 내림차순으로 읽는다")
    void readsNewestFirstWithAStableTiebreak() {
        givenEmptyPage();

        service.getHistoryPage(0, 20);

        Sort sort = capturePageable().getSort();
        assertThat(sort).containsExactly(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id"));
    }

    @Test
    @DisplayName("페이지 크기는 상한을 넘지 못한다")
    void clampsPageSizeToTheMaximum() {
        givenEmptyPage();

        service.getHistoryPage(0, 5_000);

        assertThat(capturePageable().getPageSize()).isEqualTo(MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("0 이하의 페이지 크기는 1건으로 올린다")
    void raisesNonPositivePageSize() {
        givenEmptyPage();

        service.getHistoryPage(0, 0);

        assertThat(capturePageable().getPageSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("음수 페이지 번호는 첫 페이지로 본다")
    void treatsNegativePageAsTheFirst() {
        givenEmptyPage();

        service.getHistoryPage(-3, 20);

        assertThat(capturePageable().getPageNumber()).isZero();
    }

    @Test
    @DisplayName("응답에 다음 페이지 존재 여부가 담긴다")
    void reportsWhetherMorePagesFollow() {
        AnalysisHistorySummaryDTO row = new AnalysisHistorySummaryDTO(
                1L, "検討させていただきます", 70, "EMAIL", "CAUTION", LocalDateTime.now());
        // 전체 5건 중 첫 2건
        given(repository.findSummaries(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(row, row), Pageable.ofSize(2), 5));

        PageResponse<AnalysisHistorySummaryDTO> result = service.getHistoryPage(0, 2);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    @DisplayName("없는 이력을 펼치면 404 로 이어지는 예외가 난다")
    void rejectsUnknownIdOnDetail() {
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getHistory(99L))
                .isInstanceOf(HistoryNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("없는 이력을 지우려 해도 404 로 이어진다")
    void rejectsUnknownIdOnDelete() {
        // 예전에는 IllegalArgumentException 이라 400 이 나갔다. 없는 자원은 404 다.
        given(repository.existsById(99L)).willReturn(false);

        assertThatThrownBy(() -> service.deleteHistory(99L))
                .isInstanceOf(HistoryNotFoundException.class);
    }

    @Test
    @DisplayName("있는 이력은 지운다")
    void deletesAnExistingRecord() {
        given(repository.existsById(8L)).willReturn(true);

        service.deleteHistory(8L);

        verify(repository).deleteById(8L);
    }

    @Test
    @DisplayName("있는 이력은 그대로 돌려준다")
    void returnsAnExistingRecord() {
        AnalysisHistory stored = AnalysisHistory.builder()
                .userInput("ご確認ください")
                .totalScore(80)
                .category("EMAIL")
                .riskLevel("SAFE")
                .fullAnalysisJson("{}")
                .build();
        given(repository.findById(1L)).willReturn(Optional.of(stored));

        assertThat(service.getHistory(1L)).isSameAs(stored);
    }

    // --- 헬퍼 ---

    private void givenEmptyPage() {
        given(repository.findSummaries(any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of()));
    }

    private Pageable capturePageable() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(repository).findSummaries(captor.capture());
        return captor.getValue();
    }
}
