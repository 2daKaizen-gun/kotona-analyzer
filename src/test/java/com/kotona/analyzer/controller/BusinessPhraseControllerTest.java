package com.kotona.analyzer.controller;

import com.kaizen.kotona.controller.BusinessPhraseController;
import com.kaizen.kotona.service.BusinessPhraseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessPhraseController.class)
class BusinessPhraseControllerTest {

    @Autowired
    // API 호출 시뮬레이션
    private MockMvc mockMvc;

    @MockitoBean
    // 컨트롤러가 의존하는 서비스를 Mock으로 대체
    private BusinessPhraseService service;

    @Test
    @DisplayName("GET /api/phrases 호출 시에 200 OK랑 JSON 결과가 반환되어야 함.")
    void getAllPhrasesApiTest() throws Exception {
        mockMvc.perform(get("/api/phrases"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));
    }
}
