package com.kaizen.kotona.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.kotona.dto.NuanceResponseDTO;
import com.kaizen.kotona.entity.AnalysisHistory;
import com.kaizen.kotona.repository.AnalysisHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
        } catch (JsonProcessingException e) {
            throw new RuntimeException("데이터 저장 중 직렬화 오류가 발생했습니다.", e);
        }
    }

    // 분석 이력 전체 목록 조회
    @Transactional(readOnly = true)
    public List<AnalysisHistory> getHistoryList() {
        return repository.findAllByCreatedAtDesc();
    }

    // 특정 이력 삭제
    @Transactional
    public void deleteHistory(Long id) {

    }
}
