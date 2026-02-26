package com.kaizen.kotona.service;

import com.atilika.kuromoji.ipadic.Token;
import com.atilika.kuromoji.ipadic.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service

public class JapaneseTokenService {
    private final Tokenizer tokenizer;

    public JapaneseTokenService() {
        // 기본 모드로 토크나이저 초기화
        this.tokenizer = new Tokenizer();
    }

    // 일본어 문장 토큰화 및 분석 결과 반환
    public List<String> analyzeTokens(String text) {
        List<Token> tokens = tokenizer.tokenize(text);

        // 분석 결과 로그 출력 (디버깅용)
        for (Token token : tokens) {
            log.info("Token: {}, POS: {}, Reading: {}",
                    token.getSurface(),
                    token.getAllFeatures(),
                    token.getReading());
        }

        return tokens.stream()
                .map(Token::getSurface)
                .collect(Collectors.toList());
    }
}
