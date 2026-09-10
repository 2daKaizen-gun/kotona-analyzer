package com.kaizen.kotona.analyzer.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 일본어로 나가야 할 모델 출력에서 외국어 혼입을 걷어낸다.
 *
 * <p>관측된 실패는 늘 같은 모양이었다 — 모델이 한국어나 영어 단어를 쓴 뒤,
 * 곧바로 괄호에 올바른 일본어를 덧붙여 스스로 고친다.
 * 예: {@code お 가르쳐(教えて)いただけますでしょうか},
 * {@code 何卒よろしく communication(お願い申し上げます)}.
 *
 * <p>{@code smartReplies[].content} 와 {@code suggestions[].text} 는 사용자가
 * 그대로 복사해 보내는 문장이므로 괄호 주석이 남아 있어서는 안 된다.
 * 시스템 프롬프트에도 같은 제약을 적어 두었지만 프롬프트는 요청이지 보장이 아니라,
 * 여기서 결정적으로 한 번 더 거른다.
 */
public final class JapaneseOutputSanitizer {

    /** 히라가나·가타카나·한자. 괄호 안이 실제 일본어인지 판정하는 데 쓴다. */
    private static final Pattern JAPANESE =
            Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]");

    /** 한글은 일본어 비즈니스 문장에 등장할 자리가 없다. */
    private static final Pattern HANGUL = Pattern.compile("\\p{IsHangul}");

    /**
     * 홀로 선 소문자 영단어. {@code 予算や schedule 面で} 처럼 일본어 낱말 자리를
     * 영어가 대신 차지한 경우를 잡는다.
     *
     * <p>로마자 자체를 막지는 않는다. 대문자 약어(IT, PDF, URL)와 고유명사(Slack, Zoom)는
     * 일본어 비즈니스 문장에 정상적으로 등장하므로 첫 글자가 소문자인 낱말만 본다.
     * 앞뒤에 URL·메일 주소를 이루는 문자가 붙은 토큰({@code https://example.com},
     * {@code Re-send})은 낱말이 아니라 식별자의 일부이므로 제외한다.
     */
    private static final String TOKEN_CHAR = "[A-Za-z0-9@._/:?=&#%~+-]";

    private static final Pattern LOWERCASE_WORD =
            Pattern.compile("(?<!" + TOKEN_CHAR + ")[a-z]{3,}(?!" + TOKEN_CHAR + ")");

    /**
     * 외국어 낱말 + 곧바로 이어지는 괄호 주석. 앞의 공백까지 함께 걷어낸다.
     *
     * <p>괄호 바로 앞이 로마자/한글로만 이어져 있을 때만 걸리므로
     * {@code 〇月〇日（〇）} 같은 정상 표기는 건드리지 않는다.
     */
    private static final Pattern GLOSS = Pattern.compile(
            "\\s*[\\p{IsHangul}A-Za-z][\\p{IsHangul}A-Za-z\\s'-]*[(（]([^)）]*)[)）]");

    private JapaneseOutputSanitizer() {
    }

    /**
     * {@code 외국어(日本語)} 형태의 자기 교정 주석을 괄호 안 일본어로 치환한다.
     * 괄호 안에 일본어가 없으면 의도된 표기로 보고 그대로 둔다.
     */
    public static String unwrapGlosses(String text) {
        if (text == null || text.isBlank()) return text;

        Matcher matcher = GLOSS.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String inside = matcher.group(1);
            String replacement = JAPANESE.matcher(inside).find() ? inside : matcher.group();
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 교정 후에도 외국어 낱말이 남았는지. 남았다면 그 문장은 그대로 내보낼 수 없다. */
    public static boolean containsForeignWord(String text) {
        if (text == null || text.isBlank()) return false;
        return HANGUL.matcher(text).find() || LOWERCASE_WORD.matcher(text).find();
    }
}
