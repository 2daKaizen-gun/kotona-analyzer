package com.kaizen.kotona.analyzer.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.dto.PageResponse;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.exception.HistoryNotFoundException;
import com.kaizen.kotona.analyzer.exception.AnalysisFailedException;
import com.kaizen.kotona.analyzer.repository.AnalysisHistoryRepository;
import com.kaizen.kotona.analyzer.utils.Paging;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisHistoryService {

    private final AnalysisHistoryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveHistory(String userInput, NuanceResponseDTO response) {
        try {
            // DTO를 JSON 문자열로 변환
            String json = objectMapper.writeValueAsString(response);

            AnalysisHistory history = AnalysisHistory.builder()
                    .userInput(userInput)
                    .totalScore(response.totalScore())
                    .category(response.category())
                    .riskLevel(response.riskAnalysis().riskLevel())
                    .fullAnalysisJson(json)
                    .build();

            repository.save(history);
        } catch (JacksonException e) {
            throw new AnalysisFailedException("데이터 저장 중 직렬화 오류가 발생했습니다.", e);
        }
    }

    /**
     * 최신순. id 는 저장 순서이고 createdAt 도 저장할 때 채워지므로 둘의 순서는 같다 —
     * 그래서 id 하나로 정렬한다. 예전에는 (createdAt, id) 였는데, 커서는 한 열로만 이어 받을 수 있고
     * Auditing 이전의 행은 createdAt 이 null 이라 커서로 쓸 수도 없었다.
     */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("id"));

    /** 최신순 이력 목록의 한 페이지. 저장된 분석 결과 원본은 빠진 요약만 담는다. */
    @Transactional(readOnly = true)
    public PageResponse<AnalysisHistorySummaryDTO> getHistoryPage(int page, int size) {
        return PageResponse.from(repository.findSummaries(Paging.of(page, size, NEWEST_FIRST)));
    }

    /**
     * {@code before} 보다 오래된 이력. "더 보기" 가 쓴다.
     *
     * <p>{@code totalElements} 는 커서 뒤에 남은 건수다 — 전체 건수는 첫 페이지가 이미 알려 줬다.
     */
    @Transactional(readOnly = true)
    public PageResponse<AnalysisHistorySummaryDTO> getHistoryBefore(long before, int size) {
        return PageResponse.from(repository.findSummariesBefore(before, Paging.of(0, size, NEWEST_FIRST)));
    }

    /** 목록에서 한 건을 펼칠 때. 저장된 분석 결과 전체가 여기 담겨 있다. */
    @Transactional(readOnly = true)
    public AnalysisHistory getHistory(Long id) {
        return repository.findById(id).orElseThrow(() -> new HistoryNotFoundException(id));
    }

    // 특정 이력 삭제
    @Transactional
    public void deleteHistory(Long id) {
        if (!repository.existsById(id)) {
            throw new HistoryNotFoundException(id);
        }
        repository.deleteById(id);
    }
}
