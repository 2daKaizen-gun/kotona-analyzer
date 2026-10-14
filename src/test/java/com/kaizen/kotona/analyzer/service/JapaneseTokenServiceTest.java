package com.kaizen.kotona.analyzer.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    // 거절 신호는 놓인 자리로 읽는다. 아래는 신호 낱말이 들어 있지만 거절·보류가 아닌 자리들이다.
    @ParameterizedTest(name = "{0} 에서 {1} 은(는) 세지 않는다")
    @CsvSource({
            "ご確認とご検討のほど、よろしくお願いいたします。, 検討",   // 부탁
            "ご確認とご検討のほど、よろしくお願いいたします。, 確認",
            "資料を添付しましたので、ご検討ください。, 検討",
            "内容を確認し、検討した結果、ぜひお願いしたいと考えております。, 検討", // 끝난 일
            "確認いたしましたところ、問題ございませんでした。, 確認",
            "難しい課題ですが、必ず期日までに対応いたします。, 難しい",       // 양보절
            "前向きに検討いたします。来週金曜までに回答します。, 検討",       // 기한
            "社内で確認のうえ、三日以内にご連絡いたします。, 確認",
    })
    @DisplayName("부탁·끝난 일·양보절·기한 있는 약속 자리의 신호 낱말은 세지 않는다")
    void ignoresSignalsInPlacesThatAreNotRefusals(String sentence, String lemma) {
        assertThat(service.refusalLemmas(sentence)).doesNotContain(lemma);
    }

    // 같은 낱말이 거절·보류 자리에 있으면 그대로 센다. 문맥 규칙이 과하게 넓어지지 않았다는 것을 붙잡는다.
    @ParameterizedTest(name = "{0} 에서 {1} 은(는) 센다")
    @CsvSource({
            "ご提案の件、社内で検討させていただきます。, 検討",
            "前向きに検討させていただきます。, 検討",
            "申し訳ございませんが、本日中の対応は難しい状況です。, 難しい", // が 는 難しい 앞에 있다
            "それは難しいですね。, 難しい",
            "確認してから返事します。, 確認",                             // 기한 없는 회신
            "検討のうえ、必要に応じてご連絡させていただきます。, 検討",
    })
    @DisplayName("보류·거절 자리의 신호 낱말은 그대로 센다")
    void keepsSignalsWhereTheyRefuseOrDefer(String sentence, String lemma) {
        assertThat(service.refusalLemmas(sentence)).contains(lemma);
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
