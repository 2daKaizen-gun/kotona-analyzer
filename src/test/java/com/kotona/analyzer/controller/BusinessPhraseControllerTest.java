package com.kotona.analyzer.controller;

import com.kaizen.kotona.controller.BusinessPhraseController;
import com.kaizen.kotona.service.BusinessPhraseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BusinessPhraseController.class)
class BusinessPhraseControllerTest {

    @Autowired
    // API 호출 시뮬레이션
    private MockMvc mockMvc;

    @MockitoBean
    // 컨트롤러가 의존하는 서비스를 Mock으로 대체
    private BusinessPhraseService service;


}
