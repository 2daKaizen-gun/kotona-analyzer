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
    original_text TEXT -- 분석 대상 원문
) ENGINE = InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 일본어 비즈니스 숙어 사전 테이블
