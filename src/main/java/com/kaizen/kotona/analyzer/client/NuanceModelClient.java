package com.kaizen.kotona.analyzer.client;

import com.google.genai.types.GenerateContentConfig;

/**
 * 모델 호출을 감싸는 얇은 경계.
 *
 * <p>SDK 의 {@code Client.models} 는 public 필드라서 모의 객체로 대체할 수 없다.
 * 그 탓에 GeminiService 전체가 테스트에 닿지 않았다 — 입력 검증, 호출 순서, 오류 전파까지
 * 전부 실제 API 없이는 확인할 수 없었다. 이 인터페이스가 그 자리를 대신한다.
 *
 * <p>응답 객체가 아니라 문자열을 돌려주는 이유는, 서비스가 {@code text()} 외에는
 * 쓰지 않기 때문이다. 경계는 실제로 쓰는 만큼만 넓히는 편이 낫다.
 */
public interface NuanceModelClient {

    /** 모델에게 묻고 응답 본문을 그대로 돌려준다. 응답이 비어 있으면 null 일 수 있다. */
    String generate(String model, String prompt, GenerateContentConfig config);
}
