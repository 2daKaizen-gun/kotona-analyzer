package com.kaizen.kotona.analyzer.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 거절 신호 사전은 public static 이다. 예전에는 수정 가능한 LinkedHashMap 이어서, 어느 코드든
 * 실행 중에 가중치를 바꾸거나 신호를 지울 수 있었다 — 그러면 RuleLayerEvaluationTest 가
 * 고정해 둔 판정이 테스트 밖에서 조용히 달라진다.
 */
class EtiquetteConstantsTest {

    @Test
    @DisplayName("낱말 신호 사전은 실행 중에 바꿀 수 없다")
    void lemmaSignalsCannotBeChangedAtRuntime() {
        assertThatThrownBy(() -> EtiquetteConstants.SOFT_REJECTION_SIGNALS.remove("難しい"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("구 신호 사전도 바꿀 수 없다")
    void phraseSignalsCannotBeChangedAtRuntime() {
        assertThatThrownBy(() -> EtiquetteConstants.SOFT_REJECTION_PHRASES.put(
                "結構です", new EtiquetteConstants.SoftRejectionSignal(1.0, "x")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
