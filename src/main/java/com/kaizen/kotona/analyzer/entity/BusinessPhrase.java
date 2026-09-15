package com.kaizen.kotona.analyzer.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "business_phrase", // DB Table name
        // 이름을 고정해 두어야 schema.sql 이 기존 DB 에 같은 제약을 걸 수 있다.
        uniqueConstraints = @UniqueConstraint(name = "uk_business_phrase_phrase", columnNames = "phrase"),
        // 상황별 검색이 매번 테이블을 훑지 않도록. 사용자가 표현을 추가할수록 차이가 커진다.
        indexes = @Index(name = "idx_business_phrase_situation", columnList = "situation"))
@Getter
@NoArgsConstructor
@AllArgsConstructor // 모든 필드 인자로 받는 생성자 (테스트 코드용)
public class BusinessPhrase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 두 표현이 "같은가"는 콜레이션이 정한다. MySQL 기본 콜레이션(utf8mb4_0900_ai_ci)은
    // よろしく 와 ヨロシク, ハハ 와 パパ 를 같은 문자열로 보므로, 서로 다른 표현이 UNIQUE 에 걸려 거절된다.
    // 탁점과 가나 종류를 구분하는 ja_0900_as_cs_ks 로 고정한다. 기존 DB 는 schema.sql 이 이전한다.
    @Column(nullable = false,
            columnDefinition = "varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_ja_0900_as_cs_ks")
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
