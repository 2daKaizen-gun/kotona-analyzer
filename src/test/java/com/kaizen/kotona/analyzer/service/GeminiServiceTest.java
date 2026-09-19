package com.kaizen.kotona.analyzer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.errors.ClientException;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.Schema;
import com.kaizen.kotona.analyzer.client.NuanceModelClient;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.exception.InvalidInputException;
import com.kaizen.kotona.analyzer.dto.RelationshipType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 분석 한 번이 지나는 길 전체를 본다: 정규화 → 유효성 → 형태소 → 모델 → 교차검증 → 저장.
 *
 * <p>모델 호출은 돈이 든다. 그래서 "언제 부르지 않는가" 가 "무엇을 보내는가" 만큼 중요하다.
 */
@ExtendWith(MockitoExtension.class)
class GeminiServiceTest {

    @Mock
    private NuanceModelClient modelClient;
    @Mock
    private Schema nuanceResponseSchema;
    @Mock
    private JapaneseTokenService tokenService;
    @Mock
    private AnalysisValidator analysisValidator;
    @Mock
    private AnalysisHistoryService historyService;

    private GeminiService service;

    private static final String VALID_INPUT = "ご提案の件、社内で検討させていただきます。";

    /** 모델이 돌려주는 최소한의 올바른 응답. */
    private static final String MODEL_JSON = """
            {"totalScore":73,"category":"EMAIL",
             "metrics":{"politeness":35,"indirectness":23,"etiquette":15},
             "evaluation":{"summary":"요약","keigo_check":true,"cushion_phrase_check":false},
             "feedback":{"issues":[],"cultural_nuance":"해설"},
             "suggestions":[],
             "sentiment":{"polarity":"Neutral","confidence":0.85,
                          "honne":{"tatemae":"겉","trueIntent":"속","actionItem":"행동"}},
             "riskAnalysis":{"riskLevel":"CAUTION","redFlags":[],"copingStrategy":"전략"},
             "smartReplies":[]}
            """;

    @BeforeEach
    void setUp() {
        service = new GeminiService(
                modelClient, nuanceResponseSchema, new ObjectMapper(),
                tokenService, analysisValidator, historyService);
        ReflectionTestUtils.setField(service, "model", "gemini-3.6-flash");
        ReflectionTestUtils.setField(service, "maxOutputTokens", 8000);
        ReflectionTestUtils.setField(service, "temperature", 0.7f);
        ReflectionTestUtils.setField(service, "thinkingLevel", "");
    }

    @Nested
    @DisplayName("유료 호출을 아끼는 관문")
    class InputGate {

        @Test
        @DisplayName("일본어가 없는 입력은 모델을 부르기 전에 막는다")
        void refusesInputWithoutJapaneseBeforeSpendingACall() {
            assertThatThrownBy(() -> service.analyzeJapaneseNuance("hello world", RelationshipType.INTERNAL))
                    .isInstanceOf(InvalidInputException.class)
                    .hasMessageContaining("올바른 일본어");

            verifyNoInteractions(modelClient, historyService);
        }

        @Test
        @DisplayName("빈 입력과 공백도 마찬가지다")
        void refusesEmptyInput() {
            for (String blank : new String[] {"", "   ", null}) {
                assertThatThrownBy(() -> service.analyzeJapaneseNuance(blank, RelationshipType.INTERNAL))
                        .isInstanceOf(InvalidInputException.class);
            }

            verifyNoInteractions(modelClient);
        }
    }

    @Nested
    @DisplayName("모델에 보내는 것")
    class Request {

        @Test
        @DisplayName("정규화한 문장을 보낸다 — 원문 그대로가 아니다")
        void sendsTheNormalisedText() {
            // 반각 가타카나가 그대로 가면 모델과 형태소 분석이 다른 문장을 보게 된다
            givenModelAnswers();

            service.analyzeJapaneseNuance("  ｼｽﾃﾑの確認をお願いします  ", RelationshipType.INTERNAL);

            assertThat(capturePrompt()).contains("システム").doesNotContain("ｼｽﾃﾑ");
        }

        @Test
        @DisplayName("관계 맥락을 함께 보낸다")
        void sendsTheRelationshipContext() {
            // 안 보내면 모델은 늘 사내 기준으로 판단한다
            givenModelAnswers();

            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERVIEW);

            assertThat(capturePrompt()).contains(RelationshipType.INTERVIEW.name());
        }

        @Test
        @DisplayName("응답 스키마를 강제한다")
        void forcesTheResponseSchema() {
            // 스키마가 빠지면 모델이 임의의 모양으로 답하고 역직렬화가 깨진다
            givenModelAnswers();

            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL);

            GenerateContentConfig config = captureConfig();
            assertThat(config.responseMimeType()).contains("application/json");
            assertThat(config.responseSchema()).isPresent();
        }

        @Test
        @DisplayName("thinking 레벨은 설정돼 있을 때만 붙인다")
        void onlySendsThinkingWhenConfigured() {
            // 구세대 모델에 thinking 을 보내면 400 이다
            givenModelAnswers();
            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL);
            assertThat(captureConfig().thinkingConfig()).isEmpty();

            ReflectionTestUtils.setField(service, "thinkingLevel", "high");
            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL);
            assertThat(captureConfig().thinkingConfig()).isPresent();
        }
    }

    @Nested
    @DisplayName("응답을 다루는 방식")
    class Response {

        @Test
        @DisplayName("모델 응답을 그대로 믿지 않고 검증기를 거쳐 돌려준다")
        void alwaysRunsTheAnswerThroughTheValidator() {
            givenModelAnswers();
            NuanceResponseDTO validated = validatedResult();
            given(analysisValidator.validate(any(), anyString(), any(), anyBoolean()))
                    .willReturn(validated);

            NuanceResponseDTO result = service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.EXTERNAL);

            assertThat(result).isSameAs(validated);
        }

        @Test
        @DisplayName("형태소 분석 결과를 검증기에 넘긴다")
        void handsTheTokenAnalysisToTheValidator() {
            // 경어가 실제로 쓰였는지는 형태소 분석만 안다. 안 넘기면 감점 규칙이 죽는다.
            givenModelAnswers();
            given(tokenService.hasPoliteEnding(anyString())).willReturn(true);

            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL);

            verify(analysisValidator).validate(any(), anyString(), any(), eq(true));
        }

        @Test
        @DisplayName("저장은 검증을 마친 결과로 한다")
        void persistsTheValidatedResultRatherThanTheRawAnswer() {
            // 원본을 저장하면 이력에서 펼쳤을 때 화면에 보였던 것과 다른 값이 나온다
            givenModelAnswers();
            NuanceResponseDTO validated = validatedResult();
            given(analysisValidator.validate(any(), anyString(), any(), anyBoolean()))
                    .willReturn(validated);

            service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL);

            verify(historyService).saveHistory(anyString(), same(validated));
        }

        @Test
        @DisplayName("빈 응답은 저장하지 않는다")
        void doesNotPersistAnEmptyAnswer() {
            given(modelClient.generate(anyString(), anyString(), any())).willReturn("   ");

            assertThatThrownBy(() -> service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL))
                    .isInstanceOf(RuntimeException.class);

            verify(historyService, never()).saveHistory(anyString(), any());
        }

        @Test
        @DisplayName("응답이 JSON 이 아니어도 내부 사정을 내보이지 않는다")
        void hidesParsingFailuresFromTheClient() {
            given(modelClient.generate(anyString(), anyString(), any())).willReturn("이건 JSON 이 아니다");

            assertThatThrownBy(() -> service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("잠시 후 다시 시도")
                    // Jackson 의 메시지에는 파서 위치와 클래스 이름이 담긴다
                    .hasMessageNotContaining("Jackson")
                    .hasMessageNotContaining("NuanceResponseDTO");

            verify(historyService, never()).saveHistory(anyString(), any());
        }
    }

    @Nested
    @DisplayName("오류를 넘기는 방식")
    class ErrorPropagation {

        @Test
        @DisplayName("모델 오류는 감싸지 않고 그대로 올린다")
        void letsModelErrorsThrough() {
            // 감싸면 GlobalExceptionHandler 가 429·502 로 구분하지 못하고 전부 500 이 된다
            given(modelClient.generate(anyString(), anyString(), any()))
                    .willThrow(new ClientException(429, "RESOURCE_EXHAUSTED", "quota"));

            assertThatThrownBy(() -> service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL))
                    .isInstanceOf(ClientException.class);
        }

        @Test
        @DisplayName("저장이 실패해도 내부 메시지를 내보이지 않는다")
        void hidesPersistenceFailures() {
            givenModelAnswers();
            given(analysisValidator.validate(any(), anyString(), any(), anyBoolean()))
                    .willReturn(validatedResult());
            willThrow(new RuntimeException("Duplicate entry for key 'PRIMARY'"))
                    .given(historyService).saveHistory(anyString(), any());

            assertThatThrownBy(() -> service.analyzeJapaneseNuance(VALID_INPUT, RelationshipType.INTERNAL))
                    .hasMessageNotContaining("Duplicate entry")
                    .hasMessageNotContaining("PRIMARY");
        }
    }

    // --- 헬퍼 ---

    private void givenModelAnswers() {
        given(modelClient.generate(anyString(), anyString(), any())).willReturn(MODEL_JSON);
    }

    private boolean anyBoolean() {
        return org.mockito.ArgumentMatchers.anyBoolean();
    }

    private String capturePrompt() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(modelClient, atLeastOnce())
                .generate(anyString(), captor.capture(), any());
        return captor.getValue();
    }

    private GenerateContentConfig captureConfig() {
        ArgumentCaptor<GenerateContentConfig> captor =
                ArgumentCaptor.forClass(GenerateContentConfig.class);
        verify(modelClient, atLeastOnce())
                .generate(anyString(), anyString(), captor.capture());
        return captor.getValue();
    }

    private NuanceResponseDTO validatedResult() {
        try {
            return new ObjectMapper().readValue(MODEL_JSON, NuanceResponseDTO.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
