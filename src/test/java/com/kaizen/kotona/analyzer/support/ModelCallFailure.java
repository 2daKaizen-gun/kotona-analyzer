package com.kaizen.kotona.analyzer.support;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모델 호출이 왜 막혔는가.
 *
 * <p>가려야 하는 것은 둘이다. <b>쿼터가 비어서</b> 막힌 것은 그날의 사정이고 다음 날 같은 명령이
 * 이어 간다. <b>그 밖의 이유로</b> 막힌 것은 우리 쪽 문제다 — 키가 죽었거나, 모델 이름이
 * 사라졌거나, 스키마가 거절당했거나.
 *
 * <p>예전에는 둘 다 {@code 호출 실패: ClientException} 한 줄로 적혔다. 그래서 측정은 어느
 * 쪽이든 "쿼터가 비었다고 보고 중단한다" 고 출력하고 초록불로 끝났다 — 키가 죽은 날에도
 * 똑같이 그렇게 끝난다. 쿼터를 핑계로 쓸 수 있으면 측정 도구는 무엇도 알려 주지 않는다.
 *
 * <p>쿼터 안에서도 한 번 더 갈린다. 하루 한도와 분당 한도가 같은 429 로 오지만,
 * 안내되는 대기 시간이 다르다 — 분당 제한은 몇 초, 하루 한도는 몇 시간이다. 이것을 가리지
 * 않으면 몇 초만 기다리면 되는 문장을 영구히 건너뛰게 된다({@link #retryAfter()}).
 *
 * @param quotaExhausted 무료 티어 한도에 걸린 것인가(429 RESOURCE_EXHAUSTED)
 * @param summary 사람이 읽을 한 줄. 쿼터면 언제 다시 열리는지까지 적는다
 * @param retryAfter 다시 열릴 때까지. 안내가 없으면 {@code null}
 */
public record ModelCallFailure(boolean quotaExhausted, String summary, Duration retryAfter) {

    /** 한 줄로 읽히는 길이까지만 남긴다. 그 뒤는 스택과 JSON 이라 터미널만 어지럽다. */
    private static final int SUMMARY_LIMIT = 160;

    private static final Pattern RETRY_HINT = Pattern.compile("retry in ([0-9hm.]+s)");

    /** 「retry in 22h4m30.329911641s」 — 비는 자리가 있을 수 있어 세 조각을 따로 받는다. */
    private static final Pattern RETRY_PARTS =
            Pattern.compile("retry in (?:(\\d+)h)?(?:(\\d+)m)?(?:([0-9.]+)s)?");

    public static ModelCallFailure of(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        // SDK 는 상태 코드를 문자열로만 준다. 둘 중 하나라도 보이면 쿼터로 읽는다 —
        // 「429」 는 코드, 「RESOURCE_EXHAUSTED」 는 Google 이 붙이는 이름이다.
        boolean quota = message.contains("RESOURCE_EXHAUSTED") || message.contains("429");
        return new ModelCallFailure(quota, summarize(e, message, quota), retryAfter(message));
    }

    /**
     * 안내된 대기 시간. 「retry in 5.04s」 와 「retry in 22h4m30.32s」 를 같은 자리에서 읽는다.
     *
     * <p>이 값이 짧으면 그날의 할당이 남아 있다는 뜻이다 — 분당 제한에 걸린 것이고, 기다리면
     * 이어서 물을 수 있다. 길면 하루가 끝난 것이다.
     */
    private static Duration retryAfter(String message) {
        Matcher hint = RETRY_PARTS.matcher(message);
        if (!hint.find()) {
            return null;
        }
        long hours = hint.group(1) == null ? 0 : Long.parseLong(hint.group(1));
        long minutes = hint.group(2) == null ? 0 : Long.parseLong(hint.group(2));
        double seconds = hint.group(3) == null ? 0 : Double.parseDouble(hint.group(3));
        return Duration.ofHours(hours).plusMinutes(minutes).plusMillis(Math.round(seconds * 1000));
    }

    private static String summarize(RuntimeException e, String message, boolean quota) {
        if (quota) {
            Matcher retry = RETRY_HINT.matcher(message);
            return "쿼터 소진 (429 RESOURCE_EXHAUSTED)"
                    + (retry.find() ? " — %s 뒤에 다시 열린다".formatted(retry.group(1)) : "");
        }
        String firstLine = message.lines().findFirst().orElse(message);
        return "%s: %s".formatted(e.getClass().getSimpleName(),
                firstLine.length() > SUMMARY_LIMIT ? firstLine.substring(0, SUMMARY_LIMIT) + "…" : firstLine);
    }
}
