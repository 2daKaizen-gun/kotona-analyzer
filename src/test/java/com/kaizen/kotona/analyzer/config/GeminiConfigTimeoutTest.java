package com.kaizen.kotona.analyzer.config;

import com.google.genai.types.HttpOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
    @DisplayName("기본값은 관측된 느린 꼬리보다 넉넉하다")
    void theDefaultLeavesRoomForASlowCall() {
        // 느린 쪽 꼬리가 79초까지 관측됐다. 기본값이 그보다 짧으면 정상 호출을 끊는다.
        HttpOptions options = GeminiConfig.httpOptions(180_000);

        assertThat(options.timeout()).isPresent();
        assertThat(options.timeout().orElseThrow()).isGreaterThan(79_000);
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
