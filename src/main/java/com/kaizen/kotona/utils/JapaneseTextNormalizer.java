package com.kaizen.kotona.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class JapaneseTextNormalizer {
    // 일본어(히라가나, 가타카나, 한자)가 포함되어 있는지 확인하는 패턴
    private static final Pattern VALID_CHARS = Pattern.compile("[\\u3040-\\u309F\\u30A0-\\u30FF\\u4E00-\\u9FFF]");

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";

        // 전각/반각 표준화
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);
        // 앞뒤 공백 제거
        return normalized.trim();
    }

    // 최소한의 유효성 검사
    public static boolean isValid(String input) {
        if (input == null || input.isBlank()) return false;
        // 문장 내 일본어, 알파벳, 숫자가 하나라도 포함인지 확인
        return VALID_CHARS.matcher(input).find();
    }
}