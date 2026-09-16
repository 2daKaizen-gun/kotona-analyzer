-- 기본 사전. 반복 마이그레이션이라 이 파일이 바뀔 때마다 다시 적용된다.
--
-- 동작이 하나 달라졌다. 예전에는 data.sql 이 매 부팅 실행돼, 사용자가 기본 표현을
-- 지워도 다음 부팅 때 되살아났다(의도된 동작이라고 적혀 있었다).
-- 이제는 이 파일이 바뀔 때만 실행되므로, 지운 표현은 지워진 채로 남는다.
-- 대신 여기에 표현을 추가하면 모든 데이터베이스에 자동으로 반영된다 —
-- 되살리기를 잃는 대신 "기본 사전의 단일 출처" 를 얻는다.
--
-- WHERE NOT EXISTS 를 그대로 둔 이유: 이 파일이 바뀔 때마다 전체가 다시 실행되므로,
-- 이미 있는 행을 건드리지 않아야 사용자가 뜻을 고쳐 둔 것이 덮이지 않는다.

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
