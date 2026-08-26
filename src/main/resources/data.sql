-- 접속한 datasource 의 DB 를 그대로 사용한다.
-- (USE 문을 두면 DB_NAME 을 바꿨을 때 엉뚱한 DB 를 건드린다)
--
-- 멱등성 설계 노트:
--   예전에는 TRUNCATE 후 전량 INSERT 했지만, 그러면 부팅할 때마다 테이블이 비워져
--   나중에 사용자가 추가한 숙어까지 함께 날아간다.
--   그렇다고 INSERT IGNORE 로 바꾸는 것만으로는 부족하다. IGNORE 는 UNIQUE 제약이
--   있어야 동작하는데, 이미 중복 행이 쌓인 테이블에는 Hibernate 가 유니크 인덱스를
--   만들지 못해 조용히 실패하고, 결국 부팅마다 행이 계속 늘어난다.
--   그래서 인덱스 존재 여부와 무관하게 동작하는 WHERE NOT EXISTS 방식을 쓴다.
--   (기존 중복 행은 schema.sql 이 먼저 정리한다)

-- 1. 비즈니스 이메일/채팅 핵심 표현
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    '承知いたしました' AS phrase,
    '알겠습니다 (확인 및 수락)' AS meaning,
    'EMAIL' AS situation,
    5 AS politeness_level,
    'ご依頼の件、承知いたしました。速やかに対応いたします。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    'お含み置きください' AS phrase,
    '참고해 주시기 바랍니다 (미리 양해 구함)' AS meaning,
    'NOTIFICATION' AS situation,
    4 AS politeness_level,
    '来週月曜日はシステムメンテナンスのため、お含み置きください。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    '検討させていただきます' AS phrase,
    '검토하겠습니다 (완곡한 보류/거절 시그널)' AS meaning,
    'NEGOTIATION' AS situation,
    3 AS politeness_level,
    '今回のご提案につきましては、一度社内で検討させていただきます。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

-- 2. 미팅/구두 보고 시 필수 표현
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    '左様でございますか' AS phrase,
    '그러하십니까? (정중한 맞장구)' AS meaning,
    'MEETING' AS situation,
    4 AS politeness_level,
    '左様でございますか。詳細について伺ってもよろしいでしょうか。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    '恐縮でございますが' AS phrase,
    '죄송합니다만 / 실례지만 (쿠션어)' AS meaning,
    'CUSHION' AS situation,
    5 AS politeness_level,
    '恐縮でございますが、もう一度ご説明いただけますでしょうか。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

-- 3. IT 실무/프로젝트 상황
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    '念のため' AS phrase,
    '만약을 위해 (확인 강조)' AS meaning,
    'CONFIRMATION' AS situation,
    2 AS politeness_level,
    '念のため、修正したソースコードを共有いたします。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);

INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example)
SELECT * FROM (SELECT
    'お手数ですが' AS phrase,
    '번거로우시겠지만 (요청 시 필수)' AS meaning,
    'REQUEST' AS situation,
    4 AS politeness_level,
    'お手数ですが、サーバーの再起動をお願いいたします。' AS usage_example) AS t
WHERE NOT EXISTS (SELECT 1 FROM business_phrase b WHERE b.phrase = t.phrase);
