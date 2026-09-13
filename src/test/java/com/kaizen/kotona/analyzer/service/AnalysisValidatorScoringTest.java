package com.kaizen.kotona.analyzer.service;

import com.kaizen.kotona.analyzer.dto.EvaluationDTO;
import com.kaizen.kotona.analyzer.dto.FeedbackDTO;
import com.kaizen.kotona.analyzer.dto.HonneDTO;
import com.kaizen.kotona.analyzer.dto.MetricsDTO;
import com.kaizen.kotona.analyzer.dto.NuanceResponseDTO;
import com.kaizen.kotona.analyzer.dto.RiskAnalysisDTO;
import com.kaizen.kotona.analyzer.dto.SentimentDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 점수 감점과 리스크 등급 산출을 본다.
 * (일본어 필드 교정은 AnalysisValidatorSanitizeTest 가 본다)
 *
 * <p>모델 점수를 그대로 믿지 않는 것이 이 클래스의 존재 이유다. 형태소 분석과
 * 키워드 규칙으로 교차 검증해 모델이 후하게 준 점수를 깎고, 모델이 놓친
 * 위험 신호를 되살린다.
 */
class AnalysisValidatorScoringTest {

    private final AnalysisValidator validator = new AnalysisValidator();

    /** 쿠션어·완곡어미·소프트리젝션 키워드가 하나도 없는 평범한 문장. */
    private static final String PLAIN = "明日会議があります。";

    @Nested
    @DisplayName("점수 감점")
    class Scoring {

        @Test
        @DisplayName("경어 없이 정중도가 30 이상이면 10점 깎는다")
        void penalisesPolitenessClaimedWithoutKeigo() {
            NuanceResponseDTO result = validate(metrics(35, 10, 10), PLAIN, "INTERNAL", false);

            assertThat(result.metrics().politeness()).isEqualTo(25);
        }

        @Test
        @DisplayName("경어가 실제로 있으면 정중도를 깎지 않는다")
        void keepsPolitenessWhenKeigoIsPresent() {
            NuanceResponseDTO result = validate(metrics(35, 10, 10), PLAIN, "INTERNAL", true);

            assertThat(result.metrics().politeness()).isEqualTo(35);
        }

        @Test
        @DisplayName("정중도가 30 미만이면 경어가 없어도 깎지 않는다")
        void leavesLowPolitenessAlone() {
            NuanceResponseDTO result = validate(metrics(29, 10, 10), PLAIN, "INTERNAL", false);

            assertThat(result.metrics().politeness()).isEqualTo(29);
        }

        @Test
        @DisplayName("쿠션어 없이 에티켓이 20 이상이면 10점, 완곡어미 없이 간접성이 20 이상이면 5점 깎는다")
        void penalisesEtiquetteAndIndirectnessClaimedWithoutEvidence() {
            NuanceResponseDTO result = validate(metrics(10, 25, 25), PLAIN, "INTERNAL", true);

            assertThat(result.metrics().etiquette()).isEqualTo(15);
            assertThat(result.metrics().indirectness()).isEqualTo(20);
        }

        @Test
        @DisplayName("쿠션어와 완곡어미가 실제로 있으면 깎지 않는다")
        void keepsScoresWhenEvidenceIsPresent() {
            // お手数 는 쿠션어, いただけますか 는 완곡어미
            String polite = "お手数ですが、ご対応いただけますか。";

            NuanceResponseDTO result = validate(metrics(10, 25, 25), polite, "INTERNAL", true);

            assertThat(result.metrics().etiquette()).isEqualTo(25);
            assertThat(result.metrics().indirectness()).isEqualTo(25);
        }

        @Test
        @DisplayName("총점은 모델이 준 값이 아니라 감점 후 지표의 합이다")
        void recomputesTotalFromAdjustedMetrics() {
            // 모델은 100 을 주장하지만 근거가 없다: 35→25, 25→20, 25→15
            NuanceResponseDTO aiResponse = response(metrics(35, 25, 25), "SAFE", List.of(), "EMAIL", 100);

            NuanceResponseDTO result = validator.validate(aiResponse, PLAIN, "INTERNAL", false);

            assertThat(result.totalScore()).isEqualTo(60);
        }

        @Test
        @DisplayName("감점해도 0 아래로 내려가지 않는다")
        void neverFallsBelowZero() {
            // 지표 상한을 넘는 값이 와도 음수가 나오면 안 된다
            NuanceResponseDTO result = validate(new MetricsDTO(30, 20, 20), PLAIN, "INTERNAL", false);

            assertThat(result.metrics().politeness()).isGreaterThanOrEqualTo(0);
            assertThat(result.metrics().etiquette()).isGreaterThanOrEqualTo(0);
            assertThat(result.metrics().indirectness()).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("리스크 등급")
    class Risk {

        @Test
        @DisplayName("検討 하나면 사내에서는 주의다")
        void flagsKentouAsCaution() {
            // 検討 0.5 × INTERNAL 1.0 = 0.5 → CAUTION(0.3 이상)
            NuanceResponseDTO result = validateRisk("社内で検討させていただきます。", "INTERNAL", "SAFE");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("CAUTION");
        }

        @Test
        @DisplayName("難しい 는 사내에서도 위험이다")
        void flagsMuzukashiiAsDanger() {
            // 難しい 0.8 × 1.0 = 0.8 → DANGER(0.7 이상)
            NuanceResponseDTO result = validateRisk("それは難しいですね。", "INTERNAL", "SAFE");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("DANGER");
        }

        @Test
        @DisplayName("確認 은 정중한 표현에도 흔해서 단독으로는 안전하다")
        void doesNotFlagKakuninAlone() {
            // 確認 0.2 × EXTERNAL 1.2 = 0.24 → 아직 SAFE
            NuanceResponseDTO result = validateRisk("ご確認をお願いします。", "EXTERNAL", "SAFE");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("SAFE");
        }

        @Test
        @DisplayName("같은 確認 도 면접에서는 주의로 올라간다")
        void escalatesKakuninAtInterview() {
            // 確認 0.2 × INTERVIEW 1.5 = 0.3 → CAUTION 경계에 닿는다
            NuanceResponseDTO result = validateRisk("ご確認をお願いします。", "INTERVIEW", "SAFE");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("CAUTION");
        }

        @Test
        @DisplayName("관계가 null 이면 사내로 본다")
        void treatsMissingRelationshipAsInternal() {
            NuanceResponseDTO withNull = validateRisk("ご確認をお願いします。", null, "SAFE");
            NuanceResponseDTO asInternal = validateRisk("ご確認をお願いします。", "INTERNAL", "SAFE");

            assertThat(withNull.riskAnalysis().riskLevel()).isEqualTo(asInternal.riskAnalysis().riskLevel());
        }

        @Test
        @DisplayName("규칙이 못 잡아도 모델이 위험하다고 하면 위험이다")
        void keepsTheModelsVerdictWhenRulesFindNothing() {
            // 사전에 없는 완곡 거절은 규칙이 놓친다. 예전에는 이 경우 SAFE 로 덮였다.
            NuanceResponseDTO result = validateRisk(PLAIN, "INTERNAL", "DANGER");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("DANGER");
        }

        @Test
        @DisplayName("모델이 안전하다고 해도 규칙이 위험하면 위험이다")
        void keepsTheRuleVerdictWhenTheModelUnderrates() {
            NuanceResponseDTO result = validateRisk("それは難しいですね。", "INTERNAL", "SAFE");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("DANGER");
        }

        @Test
        @DisplayName("모델 등급이 null 이거나 모르는 값이면 안전으로 본다")
        void fallsBackToSafeForUnusableModelVerdicts() {
            assertThat(validateRisk(PLAIN, "INTERNAL", null).riskAnalysis().riskLevel()).isEqualTo("SAFE");
            assertThat(validateRisk(PLAIN, "INTERNAL", "???").riskAnalysis().riskLevel()).isEqualTo("SAFE");
        }

        @Test
        @DisplayName("모델 등급의 대소문자는 가리지 않는다")
        void acceptsLowercaseModelVerdicts() {
            NuanceResponseDTO result = validateRisk(PLAIN, "INTERNAL", "danger");

            assertThat(result.riskAnalysis().riskLevel()).isEqualTo("DANGER");
        }

        @Test
        @DisplayName("위험 신호는 규칙 탐지분과 모델 탐지분을 합친다")
        void mergesRedFlagsFromBothSources() {
            NuanceResponseDTO aiResponse = response(
                    metrics(40, 20, 30), "CAUTION", List.of("모델이 본 신호"), "EMAIL", 90);

            NuanceResponseDTO result =
                    validator.validate(aiResponse, "社内で検討させていただきます。", "INTERNAL", true);

            assertThat(result.riskAnalysis().redFlags())
                    .contains("모델이 본 신호")
                    .anyMatch(flag -> flag.contains("検討"));
        }

        @Test
        @DisplayName("모델 위험 신호가 null 이어도 규칙 탐지분은 남는다")
        void toleratesNullRedFlagsFromTheModel() {
            NuanceResponseDTO aiResponse = response(metrics(40, 20, 30), "SAFE", null, "EMAIL", 90);

            NuanceResponseDTO result =
                    validator.validate(aiResponse, "それは難しいですね。", "INTERNAL", true);

            assertThat(result.riskAnalysis().redFlags()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("상황별 총평 보정")
    class AdaptiveSummary {

        @Test
        @DisplayName("면접인데 90점 미만이면 경고를 앞에 붙인다")
        void warnsWhenAnInterviewScoresBelowNinety() {
            NuanceResponseDTO aiResponse = response(metrics(30, 20, 20), "SAFE", List.of(), "INTERVIEW", 70);

            NuanceResponseDTO result = validator.validate(aiResponse, PLAIN, "INTERVIEW", true);

            assertThat(result.evaluation().summary()).startsWith("[주의: 면접 상황]");
        }

        @Test
        @DisplayName("사내 채팅에서 70점 이상이면 적절하다고 알린다")
        void praisesAdequateInternalChat() {
            NuanceResponseDTO aiResponse = response(metrics(40, 20, 30), "SAFE", List.of(), "INTERNAL_CHAT", 90);

            NuanceResponseDTO result = validator.validate(aiResponse, PLAIN, "INTERNAL", true);

            assertThat(result.evaluation().summary()).startsWith("[우수: 사내 채팅]");
        }

        @Test
        @DisplayName("그 외 상황에서는 총평을 그대로 둔다")
        void leavesOtherCategoriesUntouched() {
            NuanceResponseDTO aiResponse = response(metrics(40, 20, 30), "SAFE", List.of(), "EMAIL", 90);

            NuanceResponseDTO result = validator.validate(aiResponse, PLAIN, "INTERNAL", true);

            assertThat(result.evaluation().summary()).isEqualTo(SUMMARY);
        }
    }

    // --- 헬퍼 ---

    private static final String SUMMARY = "정중한 표현입니다.";

    private NuanceResponseDTO validate(MetricsDTO metrics, String input, String relationship, boolean hasPoliteEnding) {
        return validator.validate(
                response(metrics, "SAFE", List.of(), "EMAIL", 90), input, relationship, hasPoliteEnding);
    }

    /** 리스크만 보는 경우 지표는 감점이 일어나지 않는 값으로 고정한다. */
    private NuanceResponseDTO validateRisk(String input, String relationship, String modelRiskLevel) {
        return validator.validate(
                response(metrics(20, 10, 10), modelRiskLevel, List.of(), "EMAIL", 40), input, relationship, true);
    }

    private MetricsDTO metrics(int politeness, int indirectness, int etiquette) {
        return new MetricsDTO(politeness, indirectness, etiquette);
    }

    private NuanceResponseDTO response(
            MetricsDTO metrics, String riskLevel, List<String> redFlags, String category, int totalScore) {
        return new NuanceResponseDTO(
                totalScore,
                category,
                metrics,
                new EvaluationDTO(SUMMARY, true, true),
                new FeedbackDTO(List.of(), "특이사항 없음"),
                List.of(),
                new SentimentDTO("NEUTRAL", 0.9, new HonneDTO("확인 요청", "조속한 회신 희망", "회신 대기")),
                new RiskAnalysisDTO(riskLevel, redFlags, "회신을 기다립니다."),
                List.of());
    }
}
