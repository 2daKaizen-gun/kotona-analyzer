-- 기존 데이터베이스의 현재 모양을 그대로 옮긴 베이스라인.
--
-- 여기 적힌 타입은 예전 schema.sql 이 주장하던 것이 아니라 실제 DB 를 조회해 확인한 값이다.
-- 둘은 달랐다 — schema.sql 은 category VARCHAR(50), risk_level VARCHAR(20) 이라고 했지만
-- 실제로는 둘 다 varchar(255) 였다. Hibernate(ddl-auto=update)가 엔티티에서 테이블을 만들었고,
-- schema.sql 의 CREATE TABLE 은 IF NOT EXISTS 때문에 한 번도 실행된 적이 없었기 때문이다.
-- 그 결과 schema.sql 은 존재하지 않는 스키마를 설명하는 문서로 남아 있었다.
--
-- 이미 돌아가는 DB 는 baseline-on-migrate 로 이 버전이 적용된 것으로 간주하고 건너뛴다.
-- 새 DB 만 이 파일을 실제로 실행한다. 두 경우가 같은 모양이 되도록 실물을 기준으로 삼았다.

CREATE TABLE IF NOT EXISTS analysis_history (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_input         TEXT         NOT NULL,          -- 분석 대상 원문
    total_score        INT          NOT NULL,          -- 엔티티가 primitive int 라 NOT NULL 이다
    category           VARCHAR(255),                   -- EMAIL, MEETING 등
    risk_level         VARCHAR(255),                   -- SAFE, CAUTION, DANGER
    full_analysis_json LONGTEXT,                       -- 저장된 분석 결과 전체
    created_at         DATETIME(6),                    -- @CreatedDate. 과거 행은 null 일 수 있다
    -- 목록은 항상 최신순으로 읽는다. 인덱스가 없으면 매 조회가 정렬을 다시 한다.
    INDEX idx_analysis_history_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS business_phrase (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- 콜레이션이 기본값과 다른 이유: utf8mb4_0900_ai_ci 는 よろしく 와 ヨロシク, ハハ 와 パパ 를
    -- 같은 문자열로 본다. 그러면 UNIQUE 제약이 서로 다른 표현을 중복으로 거절한다.
    -- utf8mb4_ja_0900_as_cs_ks 는 탁점과 가나 종류를 구분하고,
    -- 반각 ｶﾀｶﾅ 와 전각 カタカナ 처럼 폭만 다른 같은 단어는 같게 본다.
    phrase           VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_ja_0900_as_cs_ks NOT NULL,
    meaning          VARCHAR(255) NOT NULL,
    situation        VARCHAR(100),                     -- Situation enum 의 이름
    politeness_level INT,
    usage_example    TEXT,
    CONSTRAINT uk_business_phrase_phrase UNIQUE (phrase),
    -- 상황별 검색이 테이블 전체를 훑지 않도록
    INDEX idx_business_phrase_situation (situation)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
