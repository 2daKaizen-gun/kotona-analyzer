package com.kaizen.kotona.analyzer.utils;

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

    /**
     * 유료 API 앞의 최소 관문. 일본어 문자가 하나라도 있어야 통과한다.
     *
     * <p>알파벳이나 숫자만으로는 통과하지 못한다 — 분석할 일본어가 없는 입력에
     * 모델 호출 비용을 쓰지 않기 위해서다. 다만 판정 근거가 유니코드 한자 범위뿐이라
     * 한자를 공유하는 중국어는 걸러내지 못한다(그 경우는 모델이 걸러 준다).
     */
    public static boolean isValid(String input) {
        if (input == null || input.isBlank()) return false;
        return VALID_CHARS.matcher(input).find();
    }
}