package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.dto.AnalysisHistorySummaryDTO;
import com.kaizen.kotona.analyzer.dto.PageResponse;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.exception.HistoryNotFoundException;
import com.kaizen.kotona.analyzer.service.AnalysisHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisHistoryController.class)
class AnalysisHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisHistoryService historyService;

    @Test
    @DisplayName("목록은 페이지 형태로 나가고 저장된 분석 결과는 싣지 않는다")
    void returnsSummariesOnly() throws Exception {
        AnalysisHistorySummaryDTO row = new AnalysisHistorySummaryDTO(
                1L, "検討させていただきます", 73, "EMAIL", "CAUTION", LocalDateTime.now());
        given(historyService.getHistoryPage(anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(row), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].userInput").value("検討させていただきます"))
                .andExpect(jsonPath("$.totalElements").value(1))
                // 요약에 원본 JSON 이 섞이면 목록을 나눈 의미가 없다
                .andExpect(jsonPath("$.content[0].fullAnalysisJson").doesNotExist());
    }

    @Test
    @DisplayName("page·size 를 그대로 서비스에 넘긴다")
    void passesPagingThrough() throws Exception {
        given(historyService.getHistoryPage(anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0, false));

        mockMvc.perform(get("/api/history").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(historyService).getHistoryPage(2, 5);
    }

    @Test
    @DisplayName("한 건을 펼치면 저장된 분석 결과까지 싣는다")
    void detailCarriesTheStoredResult() throws Exception {
        // 목록이 뺀 것을 상세가 채운다. 여기서도 빠지면 행을 펼쳐도 볼 것이 없다.
        AnalysisHistory stored = AnalysisHistory.builder()
                .userInput("ご確認ください。")
                .totalScore(80)
                .category("EMAIL")
                .riskLevel("SAFE")
                .fullAnalysisJson("{\"totalScore\":80}")
                .build();
        given(historyService.getHistory(8L)).willReturn(stored);

        mockMvc.perform(get("/api/history/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userInput").value("ご確認ください。"))
                .andExpect(jsonPath("$.fullAnalysisJson").value("{\"totalScore\":80}"));
    }

    @Test
    @DisplayName("있는 id 는 지운다")
    void deletesAnExistingRecord() throws Exception {
        // 사전 삭제와 같은 코드여야 한다
        mockMvc.perform(delete("/api/history/8"))
                .andExpect(status().isNoContent());

        verify(historyService).deleteHistory(8L);
    }

    @Test
    @DisplayName("없는 id 를 펼치면 404 다")
    void detailOfUnknownIdIsNotFound() throws Exception {
        given(historyService.getHistory(anyLong())).willThrow(new HistoryNotFoundException(99L));

        mockMvc.perform(get("/api/history/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("id 가 숫자가 아니면 400 이다")
    void rejectsNonNumericId() throws Exception {
        mockMvc.perform(get("/api/history/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("없는 id 를 지우려 해도 404 다")
    void deletingUnknownIdIsNotFound() throws Exception {
        willThrow(new HistoryNotFoundException(99L)).given(historyService).deleteHistory(99L);

        mockMvc.perform(delete("/api/history/99"))
                .andExpect(status().isNotFound());
    }
}
