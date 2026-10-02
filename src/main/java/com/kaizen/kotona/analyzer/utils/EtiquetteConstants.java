package com.kaizen.kotona.analyzer.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 점수를 고치는 규칙이 보는 사전들.
 *
 * <p>이 목록이 곧 규칙의 시야다. 여기 없는 표현은 "없는 것" 으로 판정되므로, 사전의 누락은
 * 조용히 점수가 된다 — 「恐れ入りますが」 가 빠져 있던 동안, 그 쿠션어를 쓴 문장은 쿠션어를
 * 쓰지 않은 문장으로 취급되어 10점이 깎였다. 같은 공손함에 10점 차이를 만든 것은 문장이
 * 아니라 이 파일이었다.
 *
 * <p><b>기본형으로 맞춘다.</b> 문자열 포함으로 맞추면 활용형에 걸려 넘어진다 — 사전의
 * 「考えておく」 는 실제 문장 「考えておきます」 를 잡지 못했다. Kuromoji 가 환원한 기본형을
 * 쓰면 활용에 흔들리지 않는다. 다만 「念のため」 처럼 여러 낱말이 굳어진 표현은 기본형이
 * 따로 없으므로 구(句) 목록으로 함께 둔다.
 *
 * <p>목록을 늘리거나 가중치를 바꿀 때는 {@code src/test/resources/evaluation/business-sentences.json}
 * 의 라벨된 문장과 맞대어 확인한다({@code RuleLayerEvaluationTest}). 가중치는 여전히 손으로 고른
 * 값이지만, 이제는 그 값이 라벨된 24문장의 판정을 재현해야 한다 — 임의로 바꾸면 테스트가 깨진다.
 */
public final class EtiquetteConstants {

    private EtiquetteConstants() {
    }

    /** 쿠션어의 기본형. 의뢰·거절 앞에 놓여 직설을 누그러뜨린다. */
    public static final Set<String> CUSHION_LEMMAS = Set.of(
            "お手数",     // お手数ですが — 접두사까지 한 낱말로 끊긴다
            "手数",       // 手数をおかけします
            "恐縮",       // 恐縮ですが / 大変恐縮ですが
            "恐れ入る",   // 恐れ入りますが — 활용형이 많아 문자열로는 번번이 샜다
            "申し訳",     // 申し訳ございませんが
            "差し支える", // 差し支えなければ
            "多忙",       // ご多忙のところ
            "忙しい"      // お忙しいところ
    );

    /** 기본형이 따로 없는 굳은 표현. 구 그대로 맞춘다. */
    public static final List<String> CUSHION_PHRASES = List.of(
            "念のため"
    );

    /** 완곡 어법 어미. 단정을 피해 상대에게 여지를 남기는 맺음이라 어미 그대로 본다. */
    public static final List<String> INDIRECT_ENDINGS = List.of(
            "でしょうか", "いただけますか", "いただけますでしょうか", "かと思われます", "ございませんか"
    );

    /** 소프트 리젝션 신호와 가중치. 프롬프트의 Risk Detection Guide 와 같은 목록을 본다. */
    /**
     * @param requires 함께 나와야 성립하는 낱말. 없으면 {@code null}.
     *                 「考える」 하나로는 보류가 아니다 — 「考えておきます」(考える+おく)는 보류지만
     *                 「貢献したいと考えております」(考える+おる)는 포부다. 짝으로만 구분된다.
     */
    public record SoftRejectionSignal(double weight, String description, String requires) {
        public SoftRejectionSignal(double weight, String description) {
            this(weight, description, null);
        }
    }

    /** 신호가 성립하는가. 열쇠 낱말이 있어야 하고, 짝을 요구하면 짝도 있어야 한다. */
    public static boolean signalMatches(Map.Entry<String, SoftRejectionSignal> signal, Set<String> lemmas) {
        return lemmas.contains(signal.getKey())
                && (signal.getValue().requires() == null || lemmas.contains(signal.getValue().requires()));
    }

    /**
     * 기본형으로 맞추는 거절 신호.
     *
     * <p>가중치는 "이 낱말 하나로 어느 등급까지 갈 수 있는가" 로 읽는다. 등급 경계는
     * CAUTION 0.3, DANGER 0.7 이고 사외는 1.2 배, 면접은 1.5 배가 곱해진다.
     * 그래서 0.8 은 단독으로 사내에서도 DANGER, 0.5 는 단독으로 CAUTION,
     * 0.2 는 단독으로는 어느 등급도 아니다.
     */
    public static final Map<String, SoftRejectionSignal> SOFT_REJECTION_SIGNALS = new LinkedHashMap<>() {{
        // 불가능을 직접 말하지 않는 거절. 사내에서도 그대로 거절로 읽힌다.
        put("難しい", new SoftRejectionSignal(0.8, "'어렵다(難しい)' 시그널 감지"));
        // 완곡하지만 결론이 난 거절이다. 「今回は見送らせていただきます」
        put("見送る", new SoftRejectionSignal(0.8, "'보류/거절(見送る)' — 이번 건은 받지 않겠다는 표현"));
        // 기한도 약속도 없는 보류. 「考えておきます」 가 활용형이라 예전에는 샜다.
        put("考える", new SoftRejectionSignal(0.6, "'생각해 보겠다'는 모호한 응답", "おく"));
        // 보류의 대표 표현. 「前向きに検討」 도 확약은 아니다.
        put("検討", new SoftRejectionSignal(0.5, "'검토(検討)' 시그널 감지"));
        // 確認 은 정중한 표현에서도 흔히 쓰이므로 단독으로는 CAUTION 이 되지 않게 낮게 잡는다.
        // 「確認のうえ改めてご連絡いたします」 는 정상적인 절차이지 거절이 아니다.
        put("確認", new SoftRejectionSignal(0.2, "'확인(確認)' 후 회신 — 즉답 회피 가능성"));
    }};
}
