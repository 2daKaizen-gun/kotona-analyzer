package com.kaizen.kotona.entity;

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

    @Column(nullable = false)
    private String phrase; // 일본어 숙어

    @Column(nullable = false)
    private String meaning; // 한국어 뜻

    private String situation; // 사용 상황 (EMAIL, MEETING, INTERVIEW 등)

    private Integer politenessLevel; // 정중도 (1~5)

    @Column(columnDefinition = "TEXT")
    private String usageExample; // 실제 비즈니스 메일, 대화 예시 문장
}
