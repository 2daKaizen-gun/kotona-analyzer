-- 사용할 데이터베이스 선택
USE kotona;

-- 기존 데이터를 다 지우고 다시 넣음
TRUNCATE TABLE business_phrase;

-- 초기 데이터 (일본 IT 실무 필수 숙어)
-- 1. 비즈니스 이메일/채팅 핵심 표현(EMAIL)
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example) VALUES
('承知いたしました', '알겠습니다 (확인 및 수락)', 'EMAIL', 5, 'ご依頼の件、承知いたしました。速やかに対応いたします。'),
('お含み置きください', '참고해 주시기 바랍니다 (미리 양해 구함)', 'NOTIFICATION', 4, '来週月曜日はシステムメンテナンスのため、お含み置きください。'),
('検討させていただきます', '검토하겠습니다 (완곡한 보류/거절 시그널)', 'NEGOTIATION', 3, '今回のご提案につきましては、一度社内で検討させていただきます。');

-- 2. 미팅/구두 보고 시 필수 표현 (MEETING)
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example) VALUES
('左様でございますか', '그러하십니까? (정중한 맞장구)', 'MEETING', 4, '左様でございますか。詳細について伺ってもよろ직후니까요.'),
('恐縮でございますが', '죄송합니다만 / 실례지만 (쿠션어)', 'CUSHION', 5, '恐縮でございますが, もう一度ご説明いただけますでしょうか。');

-- 3. IT 실무/프로젝트 상황 (PROJECT)
INSERT INTO business_phrase (phrase, meaning, situation, politeness_level, usage_example) VALUES
('念のため', '만약을 위해 (확인 강조)', 'CONFIRMATION', 2, '念のため、修正したソースコードを共有いたします。'),
('お手数ですが', '번거로우시겠지만 (요청 시 필수)', 'REQUEST', 4, 'お手数ですが, サーバーの再起動をお願いいたします。');