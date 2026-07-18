package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.service.GeminiService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GeminiController.class)
class GeminiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GeminiService geminiService;

    @Test
    @DisplayName("POST /analyze 는 유효한 바디에 대해 200을 반환하고 서비스를 호출한다.")
    void analyzeReturnsOk() throws Exception {
        when(geminiService.analyzeJapaneseNuance(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"承知いたしました\",\"relationshipType\":\"EMAIL\"}"))
                .andExpect(status().isOk());

        verify(geminiService).analyzeJapaneseNuance("承知いたしました", "EMAIL");
    }

    @Test
    @DisplayName("POST /analyze 는 text가 비어있으면 400을 반환한다.")
    void analyzeRejectsBlankText() throws Exception {
        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\",\"relationshipType\":\"EMAIL\"}"))
                .andExpect(status().isBadRequest());
    }
}
