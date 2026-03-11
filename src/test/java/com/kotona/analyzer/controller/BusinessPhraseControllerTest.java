package com.kotona.analyzer.controller;

import com.kaizen.kotona.controller.BusinessPhraseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessPhraseController.class)
class BusinessPhraseControllerTest {

    @Autowired
    // API 호출 시뮬레이션
    private MockMvc mockMvc;
}
