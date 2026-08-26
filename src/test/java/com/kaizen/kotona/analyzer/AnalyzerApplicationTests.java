package com.kaizen.kotona.analyzer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 컨텍스트 로딩 검증에는 실제 키가 필요 없으므로 더미 값을 주입한다.
// (Gen AI SDK 는 클라이언트 생성 시점에 키를 검증하지 않는다.)
@SpringBootTest(properties = "gemini.api-key=test-dummy-key")
class AnalyzerApplicationTests {

	@Test
	void contextLoads() {
	}

}
