package com.kaizen.kotona.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.kotona.dto.NuanceResponseDTO;
import com.kaizen.kotona.repository.AnalysisHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisHistoryService {
    private final AnalysisHistoryRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveHistory(String userInput, NuanceResponseDTO response) {

    }
}
