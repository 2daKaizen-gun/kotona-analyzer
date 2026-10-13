package com.kaizen.kotona.analyzer.config;

import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 모델 호출에 시간 상한이 실제로 붙는지.
 *
 * <p>SDK 기본값은 무한 대기다. 그 상태로는 업스트림이 멈춘 호출과 느린 호출이 구분되지 않고,
 * 멈춘 쪽이 톰캣 스레드를 영구히 붙잡는다. 이 테스트는 설정값이 {@code HttpOptions} 까지
 * 전달되는 경계를 고정한다 — SDK 안쪽에서 그 값을 쓰는 것까지는 여기서 확인할 수 없다.
 */
class GeminiConfigTimeoutTest {

    @Test
    @DisplayName("설정한 시간 상한이 HttpOptions 로 넘어간다")
    void carriesTheConfiguredTimeout() {
        HttpOptions options = GeminiConfig.httpOptions(45_000);

        assertThat(options.timeout()).contains(45_000);
    }

    @Test
    @DisplayName("기본값은 느린 정상 호출보다 길고, 프론트가 포기하는 시점보다 짧다")
    void theDefaultSitsBetweenTheSlowestCallAndTheFrontendsPatience() {
        // 아래: 느린 쪽 꼬리가 79초까지 관측됐다. 그보다 짧으면 정상 호출을 끊는다.
        // 위: kotona-web 은 110초에 끊고 "다시 시도" 를 띄운다. 그보다 길면 사용자가 떠난 뒤에
        //     결과를 저장하고, 다시 누른 만큼 쿼터가 한 번 더 나간다. 180초였을 때가 그랬다.
        assertThat(GeminiConfig.DEFAULT_TIMEOUT_MS).isGreaterThan(79_000).isLessThan(110_000);
        assertThat(GeminiConfig.httpOptions(GeminiConfig.DEFAULT_TIMEOUT_MS).timeout())
                .contains(GeminiConfig.DEFAULT_TIMEOUT_MS);
    }

    @Test
    @DisplayName("application.yaml 의 기본값이 상수와 같다")
    void theYamlDefaultMatchesTheConstant() throws IOException {
        // 실제로 쓰이는 것은 yaml 쪽 기본값이다. 상수만 고치고 yaml 을 잊으면 위 테스트는
        // 통과하면서 운영은 옛 값으로 돈다.
        try (InputStream yaml = getClass().getResourceAsStream("/application.yaml")) {
            String text = new String(yaml.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(text).contains("${GEMINI_TIMEOUT_MS:" + GeminiConfig.DEFAULT_TIMEOUT_MS + "}");
        }
    }

    @Test
    @DisplayName("0 이나 음수는 거부한다 — 무한 대기로 돌아가는 설정이다")
    void refusesToWaitForever() {
        assertThatThrownBy(() -> GeminiConfig.httpOptions(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("gemini.timeout-ms");
        assertThatThrownBy(() -> GeminiConfig.httpOptions(-1))
                .isInstanceOf(IllegalStateException.class);
    }
}
