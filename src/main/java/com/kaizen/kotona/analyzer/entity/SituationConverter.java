package com.kaizen.kotona.analyzer.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Situation 을 DB 에는 평범한 문자열로 저장한다.
 *
 * <p>@Enumerated(EnumType.STRING) 을 쓰면 Hibernate 가 새 테이블을 만들 때
 * {@code situation IN ('EMAIL', ...)} CHECK 제약을 함께 건다(새 DB 에서 확인했다).
 * 그러면 enum 에 상황을 하나 추가해도 이미 만들어진 DB 는 새 값을 거부하고,
 * ddl-auto=update 는 CHECK 제약을 고쳐 주지 않는다. 컨버터로 매핑하면 컬럼은 제약 없는
 * VARCHAR 가 되어, 상황 추가가 코드 변경만으로 끝난다.
 *
 * <p>값 검증은 요청 단계(enum 역직렬화)에서 이미 하므로 DB 제약이 없어도 API 로는
 * 잘못된 값이 들어오지 않는다.
 */
@Converter
public class SituationConverter implements AttributeConverter<Situation, String> {

    @Override
    public String convertToDatabaseColumn(Situation situation) {
        return situation == null ? null : situation.name();
    }

    /**
     * DB 에 enum 에 없는 값이 있으면 데이터와 코드가 어긋난 것이다. 클라이언트 잘못이 아니므로
     * IllegalArgumentException(→ 400) 이 아니라 IllegalStateException(→ 500) 으로 올린다.
     */
    @Override
    public Situation convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Situation.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "DB 의 situation 값 '" + value + "' 이(가) Situation enum 에 없습니다. 데이터나 enum 을 맞추세요.", e);
        }
    }
}
