package com.kaizen.kotona.analyzer.support;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 모델 호출이 왜 막혔는가.
 *
 * <p>가려야 하는 것은 "그날의 사정" 과 "우리 쪽 고장" 이다. 앞쪽은 다음 실행이 이어 가면 되고,
 * 뒤쪽은 지금 고쳐야 한다. 예전에는 둘 다 {@code 호출 실패: ClientException} 한 줄로 적혔고,
 * 그래서 측정은 어느 쪽이든 "쿼터가 비었다" 고 출력하며 초록불로 끝났다 — 키가 죽은 날에도
 * 똑같이 그렇게 끝난다. 쿼터를 핑계로 쓸 수 있으면 측정 도구는 무엇도 알려 주지 않는다.
 *
 * <p>사정 쪽도 한 덩이가 아니다. 셋 다 우리가 고칠 수 없는 일이지만 다음 수가 다르다 —
 * 하루 한도는 내일, 분당 한도는 몇 초 뒤, 모델 과부하는 잠깐 뒤에 다시 물으면 된다.
 * 그래서 {@link Kind} 로 넷을 나눈다.
 *
 * @param kind 어느 종류의 실패인가
 * @param summary 사람이 읽을 한 줄
 * @param retryAfter 안내된 대기 시간. 안내가 없으면 {@code null}
 */
public record ModelCallFailure(Kind kind, String summary, Duration retryAfter) {

    public enum Kind {
        /** 하루 한도를 다 썼다. 오늘은 더 묻지 못한다. */
        DAILY_LIMIT,
        /** 분당 한도. 그날의 할당은 남아 있고, 몇 초 기다리면 이어서 물을 수 있다. */
        THROTTLE,
        /** 모델 쪽 일시적 과부하(503). 우리 코드와 무관하고, 잠깐 뒤면 대개 풀린다. */
        UPSTREAM,
        /** 우리 쪽 문제. 키·모델 이름·스키마. 빨간불이어야 한다. */
        OURS
    }

    /** 한 줄로 읽히는 길이까지만 남긴다. 그 뒤는 스택과 JSON 이라 터미널만 어지럽다. */
    private static final int SUMMARY_LIMIT = 160;

    /** 기다려 볼 만한 길이의 상한. 이보다 길면 그날이 끝난 것으로 본다. */
    private static final Duration WAITABLE = Duration.ofSeconds(90);

    /** 과부하는 대기 시간을 알려 주지 않으므로 짧게 한 번만 물러선다. */
    private static final Duration UPSTREAM_BACKOFF = Duration.ofSeconds(5);

    /** 「retry in 22h4m30.329911641s」 — 비는 자리가 있을 수 있어 세 조각을 따로 받는다. */
    private static final Pattern RETRY_PARTS =
            Pattern.compile("retry in (?:(\\d+)h)?(?:(\\d+)m)?(?:([0-9.]+)s)?");

    public static ModelCallFailure of(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        Duration retryAfter = retryAfter(message);
        Kind kind = classify(e, message, retryAfter);
        return new ModelCallFailure(kind, summarize(e, message, kind, retryAfter), retryAfter);
    }

    /** 우리가 고쳐야 하는 실패인가. 이것만 빨간불이 된다. */
    public boolean ours() {
        return kind == Kind.OURS;
    }

    /** 오늘 더 물어볼 수 있는가. 하루 한도면 없다. */
    public boolean dayIsOver() {
        return kind == Kind.DAILY_LIMIT;
    }

    /**
     * 기다렸다가 한 번 더 물어볼 만한가. 그렇다면 얼마나 기다리면 되는가.
     *
     * @return 기다릴 시간, 기다려도 소용없으면 {@code null}
     */
    public Duration waitBeforeRetry() {
        return switch (kind) {
            case THROTTLE -> retryAfter;
            case UPSTREAM -> UPSTREAM_BACKOFF;
            case DAILY_LIMIT, OURS -> null;
        };
    }

    /**
     * 네 갈래로 가른다.
     *
     * <p>429 안에서 하루와 분당을 가르는 것은 안내된 대기 시간뿐이다 — 메시지에 적힌
     * 「retry in」 이 몇 초면 분당 제한이고, 몇 시간이면 하루가 끝난 것이다.
     *
     * <p>503 은 2026-10-08 의 실행에서 처음 받았다({@code This model is currently
     * experiencing high demand}). 그때 이 분류가 429 만 사정으로 보고 있었던 탓에 측정이
     * 빨간불로 끝났다 — 모델이 붐비는 것은 우리 잘못이 아니다.
     */
    private static Kind classify(RuntimeException e, String message, Duration retryAfter) {
        if (message.contains("RESOURCE_EXHAUSTED") || message.contains("429")) {
            return retryAfter != null && retryAfter.compareTo(WAITABLE) <= 0
                    ? Kind.THROTTLE
                    : Kind.DAILY_LIMIT;
        }
        if (message.contains("UNAVAILABLE")
                || message.contains("DEADLINE_EXCEEDED")
                || message.contains("503")
                || message.contains("504")
                || message.contains("500 INTERNAL")
                || e.getClass().getSimpleName().equals("ServerException")) {
            return Kind.UPSTREAM;
        }
        return Kind.OURS;
    }

    private static String summarize(RuntimeException e, String message, Kind kind, Duration retryAfter) {
        return switch (kind) {
            case DAILY_LIMIT -> "쿼터 소진 (429 RESOURCE_EXHAUSTED)"
                    + (retryAfter == null ? "" : " — %s 뒤에 다시 열린다".formatted(readable(retryAfter)));
            case THROTTLE -> "분당 제한 (429) — %s 뒤에 다시 열린다".formatted(readable(retryAfter));
            case UPSTREAM -> "모델 과부하 — 우리 쪽 문제가 아니다: " + firstLine(message);
            case OURS -> "%s: %s".formatted(e.getClass().getSimpleName(), firstLine(message));
        };
    }

    private static String firstLine(String message) {
        String firstLine = message.lines().findFirst().orElse(message);
        return firstLine.length() > SUMMARY_LIMIT ? firstLine.substring(0, SUMMARY_LIMIT) + "…" : firstLine;
    }

    private static String readable(Duration wait) {
        if (wait.toHours() > 0) {
            return "%dh%dm".formatted(wait.toHours(), wait.toMinutesPart());
        }
        return wait.toMinutes() > 0
                ? "%dm%ds".formatted(wait.toMinutes(), wait.toSecondsPart())
                : "%.1f초".formatted(wait.toMillis() / 1000.0);
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
}
