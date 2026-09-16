package com.kaizen.kotona.analyzer.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 컨버터는 DB 와 코드 사이에 앉아 있다. 여기서 값이 새면 사전 검색이 조용히 빈 결과를 낸다.
 */
class SituationConverterTest {

    private final SituationConverter converter = new SituationConverter();

    @ParameterizedTest
    @EnumSource(Situation.class)
    @DisplayName("모든 상황이 저장했다가 그대로 돌아온다")
    void roundTripsEverySituation(Situation situation) {
        // 값을 추가했는데 여기서 깨지면 그 상황의 표현이 전부 읽히지 않는다
        String stored = converter.convertToDatabaseColumn(situation);

        assertThat(stored).isEqualTo(situation.name());
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(situation);
    }

    @Test
    @DisplayName("null 은 양쪽 모두 null 로 지나간다")
    void passesNullThrough() {
        // situation 은 nullable 컬럼이다. 여기서 터지면 값이 없는 행을 못 읽는다.
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("DB 에 모르는 값이 있으면 서버 잘못으로 올린다")
    void treatsUnknownStoredValuesAsAServerFault() {
        // 클라이언트가 보낸 값이 아니라 이미 저장돼 있던 값이다. 400 으로 내리면
        // 사용자가 고칠 수 없는 일을 사용자 탓으로 돌리게 된다.
        assertThatThrownBy(() -> converter.convertToEntityAttribute("SMALLTALK"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMALLTALK");
    }

    @Test
    @DisplayName("대소문자가 다른 값도 모르는 값으로 본다")
    void doesNotGuessAtCasing() {
        // 조용히 맞춰 주면 DB 에 두 가지 표기가 섞여도 눈치채지 못한다
        assertThatThrownBy(() -> converter.convertToEntityAttribute("email"))
                .isInstanceOf(IllegalStateException.class);
    }
}
