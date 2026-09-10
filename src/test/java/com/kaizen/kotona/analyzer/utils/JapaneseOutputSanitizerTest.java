package com.kaizen.kotona.analyzer.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JapaneseOutputSanitizerTest {

    @Test
    @DisplayName("한국어 낱말에 붙은 괄호 주석을 괄호 안 일본어로 편다")
    void unwrapsKoreanGloss() {
        String actual = JapaneseOutputSanitizer.unwrapGlosses(
                "おおよその目安をお 가르쳐(教えて)いただけますでしょうか。");

        assertThat(actual).isEqualTo("おおよその目安をお教えていただけますでしょうか。");
    }

    @Test
    @DisplayName("영어 낱말에 붙은 괄호 주석도 편다")
    void unwrapsEnglishGloss() {
        String actual = JapaneseOutputSanitizer.unwrapGlosses(
                "承知いたしました。何卒よろしく communication(お願い申し上げます)。");

        assertThat(actual).isEqualTo("承知いたしました。何卒よろしくお願い申し上げます。");
    }

    @Test
    @DisplayName("전각 괄호도 동일하게 처리한다")
    void unwrapsFullWidthParentheses() {
        String actual = JapaneseOutputSanitizer.unwrapGlosses("よろしく communication（お願いします）。");

        assertThat(actual).isEqualTo("よろしくお願いします。");
    }

    @Test
    @DisplayName("괄호 앞이 일본어인 정상 표기는 건드리지 않는다")
    void keepsLegitimateParentheses() {
        String date = "お忙しい中恐縮ですが、〇月〇日（〇）までにいただけますと幸いです。";

        assertThat(JapaneseOutputSanitizer.unwrapGlosses(date)).isEqualTo(date);
    }

    @Test
    @DisplayName("괄호 안에 일본어가 없으면 의도된 표기로 보고 남긴다")
    void keepsNonJapaneseParentheses() {
        String acronym = "IT(Information Technology)の件です。";

        assertThat(JapaneseOutputSanitizer.unwrapGlosses(acronym)).isEqualTo(acronym);
    }

    @Test
    @DisplayName("교정할 것이 없는 순수 일본어는 그대로 통과한다")
    void leavesCleanJapaneseUntouched() {
        String clean = "お手数をおかけしますが、ご確認のほどよろしくお願いいたします。";

        assertThat(JapaneseOutputSanitizer.unwrapGlosses(clean)).isEqualTo(clean);
        assertThat(JapaneseOutputSanitizer.containsForeignWord(clean)).isFalse();
    }

    @Test
    @DisplayName("괄호 없이 섞인 한글은 펴지지 않으므로 혼입으로 잡힌다")
    void detectsBareHangul() {
        String mixed = JapaneseOutputSanitizer.unwrapGlosses("ご確認 부탁드립니다。");

        assertThat(JapaneseOutputSanitizer.containsForeignWord(mixed)).isTrue();
    }

    @Test
    @DisplayName("일본어 낱말 자리를 차지한 소문자 영단어를 혼입으로 잡는다")
    void detectsBareEnglishWord() {
        assertThat(JapaneseOutputSanitizer.containsForeignWord("予算や schedule 面でのご懸念")).isTrue();
        assertThat(JapaneseOutputSanitizer.containsForeignWord("ご dynamic 指定はございますか。")).isTrue();
        assertThat(JapaneseOutputSanitizer.containsForeignWord("〇〇という形で conditional に調整する")).isTrue();
    }

    @Test
    @DisplayName("대문자 약어와 고유명사는 정상 표기이므로 통과시킨다")
    void allowsAcronymsAndProperNouns() {
        assertThat(JapaneseOutputSanitizer.containsForeignWord("IT部門にURLをお送りしました。")).isFalse();
        assertThat(JapaneseOutputSanitizer.containsForeignWord("Slackにてご連絡いたします。")).isFalse();
        assertThat(JapaneseOutputSanitizer.containsForeignWord("PDFをご確認ください。")).isFalse();
    }

    @Test
    @DisplayName("하이픈·점으로 이어진 토큰은 낱말로 보지 않는다")
    void allowsHyphenatedAndDottedTokens() {
        assertThat(JapaneseOutputSanitizer.containsForeignWord("Re-sendの件、承知いたしました。")).isFalse();
        assertThat(JapaneseOutputSanitizer.containsForeignWord("https://example.com をご覧ください。")).isFalse();
    }

    @Test
    @DisplayName("null 과 빈 문자열에서 터지지 않는다")
    void toleratesEmptyInput() {
        assertThat(JapaneseOutputSanitizer.unwrapGlosses(null)).isNull();
        assertThat(JapaneseOutputSanitizer.unwrapGlosses("")).isEmpty();
        assertThat(JapaneseOutputSanitizer.containsForeignWord(null)).isFalse();
    }
}
