package com.kaizen.kotona.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.entity.AnalysisHistory;
import com.kaizen.kotona.analyzer.exception.AnalysisFailedException;
import com.kaizen.kotona.analyzer.repository.AnalysisHistoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 분석이 끝날 때마다 지나가는 저장 경로.
 *
 * <p>커버리지를 처음 재 봤을 때 이 메서드는 13줄 중 0줄이었다. 여기가 틀리면 분석은
 * 성공한 것처럼 보이는데 이력에는 엉뚱한 값이 쌓인다 — 목록에 보이는 점수와 펼쳤을 때의
 * 점수가 다르거나, 펼쳐도 읽을 수 없는 JSON 이 나온다.
 *
 * <p>AnalysisHistoryServiceTest 와 따로 두는 이유: 그쪽은 ObjectMapper 를 가짜로 둔다.
 * 여기서 확인할 것은 저장한 JSON 이 다시 읽히는가이므로 진짜가 필요하다.
 */
class AnalysisHistorySaveTest {

    private static final String MODEL_JSON = """
            {"totalScore":73,"category":"EMAIL",
             "metrics":{"politeness":35,"indirectness":23,"etiquette":15},
             "evaluation":{"summary":"요약","keigo_check":true,"cushion_phrase_check":false},
             "feedback":{"issues":[],"cultural_nuance":"해설"},
             "suggestions":[],
             "sentiment":{"polarity":"Neutral","confidence":0.85,
                          "honne":{"tatemae":"겉","trueIntent":"속","actionItem":"행동"}},
             "riskAnalysis":{"riskLevel":"CAUTION","redFlags":["검토 = 완곡한 거절"],"copingStrategy":"전략"},
             "smartReplies":[]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AnalysisHistoryRepository repository = mock(AnalysisHistoryRepository.class);
    private final AnalysisHistoryService service = new AnalysisHistoryService(repository, objectMapper);

    @Test
    @DisplayName("목록에 보일 요약 칸을 분석 결과에서 채운다")
    void fillsTheSummaryColumnsFromTheResult() throws Exception {
        service.saveHistory("ご提案の件、社内で検討させていただきます。", result());

        AnalysisHistory saved = captureSaved();
        assertThat(saved.getUserInput()).isEqualTo("ご提案の件、社内で検討させていただきます。");
        assertThat(saved.getTotalScore()).isEqualTo(73);
        assertThat(saved.getCategory()).isEqualTo("EMAIL");
        assertThat(saved.getRiskLevel()).isEqualTo("CAUTION");
    }

    @Test
    @DisplayName("저장한 전체 결과는 다시 읽으면 같은 결과다")
    void storesTheFullResultSoItReadsBackUnchanged() throws Exception {
        // 이력 상세는 이 JSON 을 펼쳐 보여 준다. 되읽히지 않으면 행은 있는데 내용이 없다.
        NuanceResponseDTO original = result();

        service.saveHistory("ご確認ください。", original);

        String stored = captureSaved().getFullAnalysisJson();
        assertThat(objectMapper.readValue(stored, NuanceResponseDTO.class)).isEqualTo(original);
    }

    @Test
    @DisplayName("요약 칸과 전체 결과가 같은 점수를 말한다")
    void summaryAndFullResultAgree() throws Exception {
        // 목록은 요약 칸을, 상세는 JSON 을 읽는다. 둘이 다르면 펼치는 순간 점수가 바뀐다.
        service.saveHistory("ご確認ください。", result());

        AnalysisHistory saved = captureSaved();
        NuanceResponseDTO stored = objectMapper.readValue(saved.getFullAnalysisJson(), NuanceResponseDTO.class);
        assertThat(saved.getTotalScore()).isEqualTo(stored.totalScore());
        assertThat(saved.getRiskLevel()).isEqualTo(stored.riskAnalysis().riskLevel());
    }

    @Test
    @DisplayName("직렬화에 실패하면 저장하지 않고, 원인 대신 우리 문구를 낸다")
    void refusesToSaveWhenSerialisationFails() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        given(broken.writeValueAsString(any())).willThrow(new JsonProcessingException("Infinite recursion (StackOverflowError) through reference chain") {});
        AnalysisHistoryService withBrokenMapper = new AnalysisHistoryService(repository, broken);

        assertThatThrownBy(() -> withBrokenMapper.saveHistory("ご確認ください。", result()))
                .isInstanceOf(AnalysisFailedException.class)
                .hasMessageNotContaining("recursion");

        verify(repository, never()).save(any());
    }

    // --- 헬퍼 ---

    private NuanceResponseDTO result() throws Exception {
        return objectMapper.readValue(MODEL_JSON, NuanceResponseDTO.class);
    }

    private AnalysisHistory captureSaved() {
        ArgumentCaptor<AnalysisHistory> captor = ArgumentCaptor.forClass(AnalysisHistory.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
