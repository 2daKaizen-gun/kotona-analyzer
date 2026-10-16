package com.kaizen.kotona.analyzer.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import com.kaizen.kotona.analyzer.utils.EtiquetteConstants;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    /**
     * 문장에 쓰인 낱말의 기본형 모음.
     *
     * <p>사전을 문자열 포함으로 맞추면 활용형에 걸려 넘어진다 — 사전에 「考えておく」 를
     * 적어 두어도 실제 문장은 「考えておきます」 라 걸리지 않았다. 기본형으로 환원하면
     * 「恐れ入りますが」 와 「恐れ入ります」 가 같은 낱말(恐れ入る)이 된다.
     */
    public Set<String> baseForms(String text) {
        return tokenizer.tokenize(text).stream()
                .map(Token::getBaseForm)
                .filter(form -> form != null && !form.equals("*"))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 거절 신호로 셀 낱말의 기본형. {@link #baseForms} 와 같지만, 문맥상 거절이 아닌 자리에 놓인
     * 신호 낱말은 빠진다.
     *
     * <p>낱말 주머니로만 보던 때 「ご確認とご検討のほど、よろしくお願いいたします」 — 상대에게 하는
     * 부탁 — 가 確認 0.2 + 検討 0.5 = 0.7 로 사내에서도 DANGER 였다. 같은 낱말이라도 놓인 자리가
     * 뜻을 정한다. 빼는 자리는 네 가지이고, 모두 문장 구조로 판정한다(낱말을 더 외우는 것이 아니다).
     * <ul>
     *   <li><b>부탁</b>: 「ご」가 붙은 検討·確認 — 내가 미루는 것이 아니라 상대에게 해 달라는 것
     *   <li><b>끝난 일</b>: 보류 낱말 뒤 동사 사슬에 「た」— 「検討した結果」 는 검토가 끝났다는 말
     *   <li><b>양보절</b>: 難しい 뒤 같은 문장에서 「が・けど」로 말이 이어짐 — 결론은 뒤에 온다
     *       (「難しい課題ですが、必ず対応いたします」)
     *   <li><b>기한</b>: 메시지 어딘가에 「〜までに回答します」 같은 기한 있는 약속이 있으면 보류
     *       낱말을 세지 않는다 — 프롬프트의 정의도 CAUTION 은 "기한도 약속도 없는 보류" 다
     * </ul>
     * 이 네 가지로 놓치게 되는 거절은 모델이 본다. 규칙에게 금지된 것은 놓침이 아니라 오경보다.
     */
    public Set<String> refusalLemmas(String text) {
        List<Token> tokens = tokenizer.tokenize(text);
        boolean deadlineGiven = hasDeadlineCommitment(tokens);

        Set<String> lemmas = new HashSet<>();
        for (int index = 0; index < tokens.size(); index++) {
            String form = tokens.get(index).getBaseForm();
            if (form == null || form.equals("*")) {
                continue;
            }
            boolean deferral = EtiquetteConstants.DEFERRAL_LEMMAS.contains(form);
            if (deferral && (deadlineGiven || isRequested(tokens, index) || isCompleted(tokens, index))) {
                continue;
            }
            if (EtiquetteConstants.CONCESSIVE_SENSITIVE_LEMMAS.contains(form) && isConceded(tokens, index)) {
                continue;
            }
            lemmas.add(form);
        }
        return Set.copyOf(lemmas);
    }

    /** 바로 앞이 존경 접두사 「ご・御」 인가. 「ご検討ください」「ご確認のほど」 */
    private static boolean isRequested(List<Token> tokens, int index) {
        if (index == 0) {
            return false;
        }
        Token previous = tokens.get(index - 1);
        return previous.getPartOfSpeechLevel1().equals("接頭詞")
                && EtiquetteConstants.REQUEST_PREFIXES.contains(previous.getSurface());
    }

    /** 뒤따르는 동사 사슬(동사·조동사·접속조사 て)에 과거 「た」 가 있는가. */
    private static boolean isCompleted(List<Token> tokens, int index) {
        for (int next = index + 1; next < tokens.size(); next++) {
            Token token = tokens.get(next);
            String pos = token.getPartOfSpeechLevel1();
            boolean chain = pos.equals("動詞") || pos.equals("助動詞")
                    || (pos.equals("助詞") && token.getSurface().equals("て"));
            if (!chain) {
                return false;
            }
            if (pos.equals("助動詞") && token.getBaseForm().equals("た")) {
                return true;
            }
        }
        return false;
    }

    /** 같은 문장 안에서 뒤에 양보의 접속조사가 오고, 그 뒤에도 말이 이어지는가. */
    private static boolean isConceded(List<Token> tokens, int index) {
        for (int next = index + 1; next < tokens.size(); next++) {
            Token token = tokens.get(next);
            if (isSentenceEnd(token)) {
                return false;
            }
            if (token.getPartOfSpeechLevel2().equals("接続助詞")
                    && EtiquetteConstants.CONCESSIVE_PARTICLES.contains(token.getSurface())) {
                return continuesAfter(tokens, next);
            }
        }
        return false;
    }

    private static boolean continuesAfter(List<Token> tokens, int index) {
        for (int next = index + 1; next < tokens.size(); next++) {
            Token token = tokens.get(next);
            if (isSentenceEnd(token)) {
                return false;
            }
            if (!token.getPartOfSpeechLevel1().equals("記号")) {
                return true;
            }
        }
        return false;
    }

    /** 「までに・以内に」 다음, 같은 문장 안에 회신·제출 낱말이 오는가. */
    private static boolean hasDeadlineCommitment(List<Token> tokens) {
        for (int index = 0; index + 1 < tokens.size(); index++) {
            boolean bound = EtiquetteConstants.DEADLINE_MARKERS.contains(tokens.get(index).getSurface())
                    && tokens.get(index + 1).getSurface().equals("に");
            if (bound && repliesLater(tokens, index + 2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean repliesLater(List<Token> tokens, int from) {
        for (int next = from; next < tokens.size(); next++) {
            Token token = tokens.get(next);
            if (isSentenceEnd(token)) {
                return false;
            }
            if (EtiquetteConstants.REPLY_LEMMAS.contains(token.getBaseForm())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSentenceEnd(Token token) {
        return token.getPartOfSpeechLevel2().equals("句点");
    }

    private static boolean isPolite(Token token) {
        if (POLITE_AUXILIARIES.contains(token.getConjugationType())) {
            return true;
        }
        return REQUEST_VERBS.contains(token.getBaseForm())
                && token.getConjugationForm().startsWith("命令");
    }
}
