package com.kaizen.kotona.analyzer.controller;

import com.kaizen.kotona.analyzer.dto.PageResponse;
import com.kaizen.kotona.analyzer.entity.BusinessPhrase;
import com.kaizen.kotona.analyzer.entity.Situation;
import com.kaizen.kotona.analyzer.exception.DuplicatePhraseException;
import com.kaizen.kotona.analyzer.exception.PhraseNotFoundException;
import com.kaizen.kotona.analyzer.service.BusinessPhraseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessPhraseController.class)
class BusinessPhraseControllerTest {

    @Autowired
    // API 호출 시뮬레이션
    private MockMvc mockMvc;

    @MockitoBean
    // 컨트롤러가 의존하는 서비스를 Mock으로 대체
    private BusinessPhraseService service;

    private static final String VALID_BODY = """
            {"phrase":"承知いたしました","meaning":"알겠습니다","situation":"EMAIL","politenessLevel":5,"usageExample":"예시"}
            """;

    @Test
    @DisplayName("GET /api/phrases 는 페이지 형태로 응답한다")
    void getAllPhrasesApiTest() throws Exception {
        BusinessPhrase phrase = new BusinessPhrase(1L, "承知いたしました", "알겠습니다", Situation.EMAIL, 5, "예시");
        given(service.getAllPhrases(anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(phrase), 0, 20, 1, 1, false));

        mockMvc.perform(get("/api/phrases"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.content[0].phrase").value("承知いたしました"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("GET /api/phrases 는 page·size 를 그대로 서비스에 넘긴다")
    void passesPagingParametersThrough() throws Exception {
        given(service.getAllPhrases(anyInt(), anyInt()))
                .willReturn(new PageResponse<>(List.of(), 2, 5, 0, 0, false));

        mockMvc.perform(get("/api/phrases").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(service).getAllPhrases(2, 5);
    }

    @Test
    @DisplayName("POST /api/phrases 는 유효한 바디에 201 과 저장된 표현을 돌려준다.")
    void createReturns201() throws Exception {
        given(service.create(any())).willReturn(
                new BusinessPhrase(10L, "承知いたしました", "알겠습니다", Situation.EMAIL, 5, "예시"));

        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.situation").value("EMAIL"));
    }

    @Test
    @DisplayName("필수값이 비면 서비스를 부르지 않고 400 과 안내 문구를 돌려준다.")
    void createRejectsBlankPhrase() throws Exception {
        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\":\" \",\"meaning\":\"알겠습니다\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("표현(phrase)은 필수입니다."));

        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("정중도가 1~5 를 벗어나면 400.")
    void createRejectsPolitenessOutOfRange() throws Exception {
        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\":\"念のため\",\"meaning\":\"만약을 위해\",\"politenessLevel\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("정중도(politenessLevel)는 1~5 사이여야 합니다."));
    }

    @Test
    @DisplayName("예문이 너무 길면 중복(409)이 아니라 길이(400)로 거절한다.")
    void createRejectsATooLongUsageExample() throws Exception {
        // 예전에는 DB 가 거절했고, 그 예외가 중복 위반과 같은 타입이라 "이미 등록된 표현" 으로 안내됐다.
        String tooLong = "例".repeat(501);

        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\":\"念のため\",\"meaning\":\"만약을 위해\",\"usageExample\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("예문(usageExample)은 500자 이하여야 합니다."));

        verify(service, never()).create(any());
    }

    @Test
    @DisplayName("situation 에 없는 값을 보내면 500 이 아니라 400 과 허용값 목록을 돌려준다.")
    void createRejectsUnknownSituation() throws Exception {
        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phrase\":\"念のため\",\"meaning\":\"만약을 위해\",\"situation\":\"EMIAL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("situation")))
                .andExpect(jsonPath("$.error", containsString("EMAIL")));
    }

    @Test
    @DisplayName("상황 검색에 없는 값을 보내면 빈 목록이 아니라 400.")
    void searchRejectsUnknownSituation() throws Exception {
        mockMvc.perform(get("/api/phrases/search").param("situation", "EMIAL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("situation")));
    }

    @Test
    @DisplayName("중복 표현은 409.")
    void createDuplicateReturns409() throws Exception {
        given(service.create(any())).willThrow(new DuplicatePhraseException("承知いたしました"));

        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("DB 제약 위반은 409 로 내보내되 SQL 원문은 숨긴다.")
    void dataIntegrityViolationHidesSql() throws Exception {
        given(service.create(any())).willThrow(new DataIntegrityViolationException(
                "Duplicate entry 'x' for key 'business_phrase.UK_phrase'"));

        mockMvc.perform(post("/api/phrases").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", not(containsString("Duplicate entry"))));
    }

    @Test
    @DisplayName("PUT /api/phrases/{id} 는 수정된 표현을, 없는 id 는 404 를 돌려준다.")
    void updateReturnsBodyOr404() throws Exception {
        given(service.update(eq(1L), any())).willReturn(
                new BusinessPhrase(1L, "承知いたしました", "알겠습니다", Situation.EMAIL, 5, "예시"));
        given(service.update(eq(99L), any())).willThrow(new PhraseNotFoundException(99L));

        mockMvc.perform(put("/api/phrases/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(put("/api/phrases/99").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/phrases/{id} 는 204, 없는 id 는 404.")
    void deleteReturns204Or404() throws Exception {
        willThrow(new PhraseNotFoundException(99L)).given(service).delete(99L);

        mockMvc.perform(delete("/api/phrases/1"))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);

        mockMvc.perform(delete("/api/phrases/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("숫자가 아닌 id 는 500 이 아니라 400.")
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/phrases/abc"))
                .andExpect(status().isBadRequest());
    }
}
