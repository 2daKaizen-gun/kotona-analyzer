package com.kaizen.kotona.analyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_phrase") // DB Table name
@Getter
@NoArgsConstructor
@AllArgsConstructor // 모든 필드 인자로 받는 생성자 (테스트 코드용)
public class BusinessPhrase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // UNIQUE: data.sql 의 INSERT IGNORE 가 재시작 시 중복 삽입을 막는 근거가 된다.
    @Column(nullable = false, unique = true)
    private String phrase; // 일본어 숙어

    @Column(nullable = false)
    private String meaning; // 한국어 뜻

    // DB 에는 제약 없는 VARCHAR 로 저장한다. @Enumerated 대신 컨버터를 쓰는 이유는 SituationConverter 참고.
    @Convert(converter = SituationConverter.class)
    @Column(length = 100)
    private Situation situation; // 사용 상황

    private Integer politenessLevel; // 정중도 (1~5)

    @Column(columnDefinition = "TEXT")
    private String usageExample; // 실제 비즈니스 메일, 대화 예시 문장

    public static BusinessPhrase create(String phrase, String meaning, Situation situation,
                                        Integer politenessLevel, String usageExample) {
        return new BusinessPhrase(null, phrase, meaning, situation, politenessLevel, usageExample);
    }

    /** PUT 은 전체 교체다. id 를 제외한 모든 필드를 덮어쓴다. */
    public void replace(String phrase, String meaning, Situation situation,
                        Integer politenessLevel, String usageExample) {
        this.phrase = phrase;
        this.meaning = meaning;
        this.situation = situation;
        this.politenessLevel = politenessLevel;
        this.usageExample = usageExample;
    }
}
