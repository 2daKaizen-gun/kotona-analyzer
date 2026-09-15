-- 분석 이력 테이블
-- IF NOT EXISTS: 이미 테이블이 있는데 또 만들려고 하면 에러.
-- 이 구문을 써야 멱등성(Idempotency)이 보장되어 서버를 여러 번 껐다 켜도 안전.
-- LONGTEXT: 일반적인 TEXT보다 훨씬 넉넉한 LONGTEXT를 사용해 데이터 유실을 방지.
-- utf8mb4: 일본어의 복잡한 한자와 이모지까지 완벽하게 저장하기 위한 설정.
CREATE TABLE IF NOT EXISTS analysis_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(50), -- 분석 카테고리 (EMAIL, CHAT 등)
    created_at DATETIME(6), -- 분석 일시
    full_analysis_json LONGTEXT, -- AI가 준 전체 JSON 데이터
    risk_level VARCHAR(20), -- 위험도(SAFE, CAUTION, DANGER)
    total_score INT, -- 종합 점수
    user_input TEXT NOT NULL, -- 분석 대상 원문 (엔티티 AnalysisHistory.userInput 과 일치)
    -- 목록은 항상 최신순으로 읽는다. 인덱스가 없으면 매 조회가 정렬을 다시 한다.
    INDEX idx_analysis_history_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일본어 비즈니스 숙어 사전 테이블
-- 참고: spring.jpa.defer-datasource-initialization=true 라서 Hibernate(ddl-auto=update)가
-- 이 스크립트보다 먼저 테이블을 만든다. 그래서 아래 CREATE 문은 평소에는 IF NOT EXISTS 로
-- 건너뛰고, ddl-auto 를 끈 환경에서만 쓰인다. 테이블 모양의 기준은 엔티티(BusinessPhrase)다.
CREATE TABLE IF NOT EXISTS business_phrase (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phrase VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_ja_0900_as_cs_ks NOT NULL, -- 일본어 숙어 표현
    meaning VARCHAR(255) NOT NULL, -- 한국어 뜻
    situation VARCHAR(100), -- 사용 상황 (Situation enum 의 이름)
    politeness_level INT, -- 정중도 단계
    usage_example TEXT, -- 실제 활용 예시 문장
    CONSTRAINT uk_business_phrase_phrase UNIQUE (phrase), -- 중복 삽입 방지
    -- 상황별 검색이 테이블 전체를 훑지 않도록
    INDEX idx_business_phrase_situation (situation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- [1] phrase 콜레이션 이전.
-- MySQL 기본 콜레이션(utf8mb4_0900_ai_ci)은 よろしく 와 ヨロシク, ハハ 와 パパ 를 같은 문자열로 본다.
-- 그러면 UNIQUE 제약 때문에 서로 다른 표현이 "중복"으로 거절된다.
-- utf8mb4_ja_0900_as_cs_ks 는 탁점과 가나 종류를 구분하고, 반각 ｶﾀｶﾅ 와 전각 カタカナ 처럼
-- 폭만 다른 같은 단어는 같게 본다.
-- MySQL 에는 ALTER ... IF 가 없어서, 아직 바뀌지 않았을 때만 실제 DDL 을 준비해 실행한다.
-- 이미 바뀌었으면 DO 0(아무것도 안 함)이 실행되므로 매 부팅 실행해도 안전하다.
SET @phrase_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'business_phrase'
        AND COLUMN_NAME = 'phrase' AND COLLATION_NAME <> 'utf8mb4_ja_0900_as_cs_ks') > 0,
    'ALTER TABLE business_phrase MODIFY phrase VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_ja_0900_as_cs_ks NOT NULL',
    'DO 0');
PREPARE phrase_stmt FROM @phrase_ddl;
EXECUTE phrase_stmt;
DEALLOCATE PREPARE phrase_stmt;

-- [2] 과거 TRUNCATE 방식에서 넘어온 중복 행 정리.
-- 같은 phrase 가 여러 개면 가장 먼저 들어온 행(최소 id)만 남긴다.
-- 중복이 없으면 아무것도 지우지 않으므로 매 부팅 실행해도 안전하다.
-- 반드시 [1] 다음에 둔다. 옛 콜레이션으로 비교하면 가나만 다른 두 표현을 중복으로 보고 하나를 지운다.
DELETE dup FROM business_phrase dup
    JOIN business_phrase keep
      ON dup.phrase = keep.phrase
     AND dup.id > keep.id;

-- [3] phrase UNIQUE 제약 보장.
-- 중복 행이 쌓여 있던 시절에 만들어진 테이블에는 이 제약이 없고, Hibernate(ddl-auto=update)는
-- 이미 있는 테이블에 추가해 주지 않는다(실제로 로컬 DB 는 여러 번 재부팅해도 생기지 않았다).
-- [2] 에서 중복을 정리했으니 여기서 건다. 이름은 엔티티의 @UniqueConstraint 와 맞춘다.
SET @unique_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'business_phrase'
        AND COLUMN_NAME = 'phrase' AND NON_UNIQUE = 0) = 0,
    'ALTER TABLE business_phrase ADD CONSTRAINT uk_business_phrase_phrase UNIQUE (phrase)',
    'DO 0');
PREPARE unique_stmt FROM @unique_ddl;
EXECUTE unique_stmt;
DEALLOCATE PREPARE unique_stmt;
