package com.kaizen.kotona.utils;

import java.text.Normalizer;
import java.util.regex.Pattern;

public class JapaneseTextNormalizer {
    // 일본어, 기본 문장 부호만 허용하는 정규식 (히라가나, 가타카나, 한자, 숫자, 알파벳)
    private static final Pattern VALID_CHARS = Pattern.compile("^[\\\\u3040-\\\\u309F\\\\u30A0-\\\\u30FF\\\\u4E00-\\\\u9FFF0-9a-zA-Z\\\\s.,!?、。！？]+$");

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";

        // NFKC 정규화 (전각 알파벳, 숫자를 반각으로, 반각 가타카나를 전각으로 통일)
        // 일본어 처리 표준
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC);

        // 불필요한 공백 제거
        normalized = normalized.trim().replaceAll("\\s+", " ");
        return normalized;
    }
}
