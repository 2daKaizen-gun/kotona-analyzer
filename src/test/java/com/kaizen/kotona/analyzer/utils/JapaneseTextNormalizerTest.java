package com.kaizen.kotona.analyzer.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 입력 정규화와 유효성 검사를 본다.
 *
 * <p>이 두 함수가 유료 API 앞의 첫 관문이다. isValid 가 통과시킨 문장만 모델에
 * 도달하므로, 여기서 새는 만큼 그대로 비용이 된다.
 */
class JapaneseTextNormalizerTest {

    @Nested
    @DisplayName("정규화")
    class Normalize {

        @Test
        @DisplayName("null 과 공백은 빈 문자열이 된다")
        void collapsesNullAndBlankToEmpty() {
            assertThat(JapaneseTextNormalizer.normalize(null)).isEmpty();
            assertThat(JapaneseTextNormalizer.normalize("")).isEmpty();
            assertThat(JapaneseTextNormalizer.normalize("   ")).isEmpty();
        }

        @Test
        @DisplayName("앞뒤 공백을 떼어 낸다")
        void trimsSurroundingWhitespace() {
            assertThat(JapaneseTextNormalizer.normalize("  承知いたしました  "))
                    .isEqualTo("承知いたしました");
        }

        @Test
        @DisplayName("반각 가타카나를 전각으로 펴서 형태소 분석이 알아보게 한다")
        void widensHalfWidthKatakana() {
            // 반각으로 들어오면 Kuromoji 가 같은 낱말로 보지 못한다
            assertThat(JapaneseTextNormalizer.normalize("ｼｽﾃﾑ")).isEqualTo("システム");
        }

        @Test
        @DisplayName("전각 영숫자는 반각으로 좁힌다")
        void narrowsFullWidthAlphanumerics() {
            assertThat(JapaneseTextNormalizer.normalize("ＡＢＣ１２３")).isEqualTo("ABC123");
        }

        @Test
        @DisplayName("같은 뜻의 서로 다른 표기가 한 형태로 모인다")
        void mapsEquivalentFormsTogether() {
            // 반각과 전각으로 입력된 같은 문장이 정규화 후 구분되지 않아야 한다
            String halfWidth = JapaneseTextNormalizer.normalize("ﾒｰﾙ1件");
            String fullWidth = JapaneseTextNormalizer.normalize("メール１件");

            assertThat(halfWidth).isEqualTo(fullWidth);
        }

        @Test
        @DisplayName("일본어 구두점은 건드리지 않는다")
        void leavesJapanesePunctuationAlone() {
            assertThat(JapaneseTextNormalizer.normalize("承知しました。よろしくお願いします。"))
                    .isEqualTo("承知しました。よろしくお願いします。");
        }
    }

    @Nested
    @DisplayName("유효성 검사")
    class IsValid {

        @Test
        @DisplayName("히라가나·가타카나·한자 중 하나라도 있으면 통과한다")
        void acceptsAnyJapaneseScript() {
            assertThat(JapaneseTextNormalizer.isValid("ください")).isTrue();   // 히라가나
            assertThat(JapaneseTextNormalizer.isValid("システム")).isTrue();   // 가타카나
            assertThat(JapaneseTextNormalizer.isValid("会議")).isTrue();       // 한자
        }

        @Test
        @DisplayName("일본어가 섞여 있으면 통과한다")
        void acceptsMixedText() {
            assertThat(JapaneseTextNormalizer.isValid("Slack で確認します")).isTrue();
        }

        @Test
        @DisplayName("null·빈 문자열·공백은 막는다")
        void rejectsEmptyInput() {
            assertThat(JapaneseTextNormalizer.isValid(null)).isFalse();
            assertThat(JapaneseTextNormalizer.isValid("")).isFalse();
            assertThat(JapaneseTextNormalizer.isValid("   ")).isFalse();
        }

        @Test
        @DisplayName("일본어가 없으면 막는다 — 여기서 새면 그대로 API 비용이다")
        void rejectsTextWithoutJapanese() {
            assertThat(JapaneseTextNormalizer.isValid("안녕하세요")).isFalse();
            assertThat(JapaneseTextNormalizer.isValid("hello world")).isFalse();
            assertThat(JapaneseTextNormalizer.isValid("12345")).isFalse();
            assertThat(JapaneseTextNormalizer.isValid("!@#$%")).isFalse();
        }

        @Test
        @DisplayName("알려진 한계: 한자 범위를 공유하는 중국어는 통과한다")
        void knownLimitationChineseSharesTheKanjiRange() {
            // 판정 근거가 유니코드 한자 범위뿐이라 중국어를 일본어와 구분하지 못한다.
            // 모델이 걸러 주므로 비용 방어라는 목적에는 문제가 없다. 바뀌면 알아차리도록 고정해 둔다.
            assertThat(JapaneseTextNormalizer.isValid("我们明天开会")).isTrue();
        }
    }
}
