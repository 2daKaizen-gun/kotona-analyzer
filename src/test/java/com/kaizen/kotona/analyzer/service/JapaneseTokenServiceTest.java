package com.kaizen.kotona.analyzer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 진짜 형태소 분석기(Kuromoji)로 돈다. 가짜로 바꾸면 이 클래스가 확인하는 유일한 것 —
 * 사전이 이 문장을 어떻게 쪼개는가 — 이 사라진다.
 *
 * <p>이 테스트가 생기기 전까지 이 로직은 한 번도 실행된 적이 없었다. GeminiServiceTest 는
 * 이 서비스를 가짜로 바꿔 두고 돌았고, 그래서 「ご確認ください」 가 무례하다고 판정되는 것을
 * 아무도 보지 못했다.
 */
class JapaneseTokenServiceTest {

    private final JapaneseTokenService service = new JapaneseTokenService();

    @ParameterizedTest(name = "정중: {0}")
    @ValueSource(strings = {
            "承知いたしました。",
            "ご確認いただけますでしょうか。",
            "申し訳ございません。",
            "よろしくお願い申し上げます。",
            "何卒よろしくお願いいたします。",
            "お待ちくださいませ。",
    })
    @DisplayName("です・ます 가 쓰인 문장은 정중하다")
    void recognisesDesuMasu(String sentence) {
        assertThat(service.hasPoliteEnding(sentence)).isTrue();
    }

    @ParameterizedTest(name = "정중: {0}")
    @ValueSource(strings = {
            "ご確認ください。",
            "ご査収ください。",
            "少々お待ちください。",
            "資料をご確認下さい。",
    })
    @DisplayName("「〜ください」 는 です・ます 가 없어도 정중하다")
    void recognisesKudasaiRequests(String sentence) {
        // 업무 메일에서 가장 흔한 의뢰 형태다. 예전에는 false 라 정중도 10점이 깎였다.
        assertThat(service.hasPoliteEnding(sentence)).isTrue();
    }

    @ParameterizedTest(name = "반말: {0}")
    @ValueSource(strings = {
            "よろしく",
            "確認して",
            "了解",
            "明日までにやっといて。",
    })
    @DisplayName("정중체가 없는 문장은 정중하지 않다")
    void rejectsPlainSentences(String sentence) {
        assertThat(service.hasPoliteEnding(sentence)).isFalse();
    }

    @ParameterizedTest(name = "반말: {0}")
    @ValueSource(strings = {
            "先生がくださった本を読んだ。",
            "お待ちなさい。",
    })
    @DisplayName("くださる 가 있어도 의뢰가 아니거나, 윗사람의 명령형이면 정중하지 않다")
    void doesNotWidenBeyondRequests(String sentence) {
        // 첫째는 존경어를 쓴 반말 문장이고, 둘째는 업무 상대에게 쓰면 결례인 명령이다.
        // 고치면서 기준을 필요 이상으로 넓히지 않았다는 것을 여기서 붙잡아 둔다.
        assertThat(service.hasPoliteEnding(sentence)).isFalse();
    }
}
