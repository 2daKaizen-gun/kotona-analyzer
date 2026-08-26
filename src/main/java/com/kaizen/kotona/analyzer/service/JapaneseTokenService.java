package com.kaizen.kotona.analyzer.service;

import com.atilika.kuromoji.ipadic.Tokenizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class JapaneseTokenService {
    private final Tokenizer tokenizer;

    public JapaneseTokenService() {
        // 기본 모드로 토크나이저 초기화
        this.tokenizer = new Tokenizer();
    }

    // 문장 정중체 포함 확인, 하이브리드 검증 핵심 로직
    public boolean hasPoliteEnding(String text) {
        return tokenizer.tokenize(text).stream()
                .anyMatch(token -> {
                    String features = token.getAllFeatures();
                    // 'です' 또는 'ます'를 포함하는 조동사인지 체크
                    return features.contains("特殊・デス") || features.contains("特殊・マス");
                });
    }
}
