package com.kaizen.kotona.analyzer.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;


@Slf4j
@Service
public class JapaneseTokenService {
    private final Tokenizer tokenizer;

    /** です・ます 계열 조동사의 활용형. でしょう・ました・ございます 도 여기에 걸린다. */
    private static final Set<String> POLITE_AUXILIARIES = Set.of("特殊・デス", "特殊・マス");

    /** 정중한 의뢰를 만드는 동사. 표기가 둘이다. */
    private static final Set<String> REQUEST_VERBS = Set.of("くださる", "下さる");

    public JapaneseTokenService() {
        // 기본 모드로 토크나이저 초기화
        this.tokenizer = new Tokenizer();
    }

    /**
     * 문장 어딘가에 정중체가 쓰였는지. 하이브리드 검증의 핵심 로직이다 — 모델이 정중도를 높게
     * 줬는데 이것이 false 면 검증기가 10점을 깎는다.
     *
     * <p>です・ます 만 보던 때는 「ご確認ください」「ご査収ください」「少々お待ちください」 가
     * 모두 false 였다. 업무 메일에서 가장 흔한 정중한 의뢰가 무례하다고 감점된 것이다.
     * 그래서 くださる 의 명령형도 센다.
     *
     * <p>명령형으로 좁히는 이유: 「先生がくださった本を読んだ」 의 くださる 는 존경어이지만
     * 문장은 반말이다. 그리고 존경 명령형 전체로 넓히지 않는 이유: 「お待ちなさい」 는
     * 윗사람이 아랫사람에게 하는 말이라 업무 상대에게 쓰면 결례다.
     */
    public boolean hasPoliteEnding(String text) {
        return tokenizer.tokenize(text).stream().anyMatch(JapaneseTokenService::isPolite);
    }

    private static boolean isPolite(Token token) {
        if (POLITE_AUXILIARIES.contains(token.getConjugationType())) {
            return true;
        }
        return REQUEST_VERBS.contains(token.getBaseForm())
                && token.getConjugationForm().startsWith("命令");
    }
}
