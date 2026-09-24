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
     * 최신순 이력 목록. 저장된 분석 결과 원본은 빠진 요약만 담는다.
     *
     * <p>createdAt 이 같거나 비어 있어도 순서가 흔들리지 않도록 id 를 보조 정렬로 둔다.
     * (Auditing 을 켜기 전에 쌓인 행은 createdAt 이 null 이다)
     */
    @Transactional(readOnly = true)
    public PageResponse<AnalysisHistorySummaryDTO> getHistoryPage(int page, int size) {
        Sort newestFirst = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageResponse.from(repository.findSummaries(Paging.of(page, size, newestFirst)));
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
