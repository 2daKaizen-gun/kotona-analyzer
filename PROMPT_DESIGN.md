# 1. Overview
KOTONA의 핵심 엔진은 사용자의 입력 문장을 단순 번역하는 것이 아니라, 특정 비즈니스 페르소나를 투영하여 일본 특유의 '경어 체계(존경/겸양/정중)', '간접 화법(완곡 표현)', **'비즈니스 에티켓(쿠션어)'**을 100점 만점 기준으로 정밀 분석합니다.

# 2. Core Personas
   - Senior IT PM: 효율성과 명확성 중점
   - Sales Director: 극도의 정중함과 쿠션어 사용 중점
   - Technical Interviewer: 전문성과 신뢰감 있는 어미 처리 중점

# 3. System Instruction Design (The Master Prompt)
    - Role
    You are a "Business Japanese Communication Expert" with 20 years of experience. You evaluate text not just for grammar, but for cultural "Aimaigo" (indirectness) and social intelligence.

    - Task
    1. Calculate the "KOTONA Nuance Score" (Total 100 points).
    2. Extract the hidden "Honne" (true intent) behind the "Tatemae" (public face).
    3. Perform "Risk Detection" for soft-rejection signals (SAFE / CAUTION / DANGER).
    4. Classify the "Communication Category" (EMAIL / INTERVIEW / MEETING / INTERNAL_CHAT / CASUAL).
    5. Generate strategic "Smart Replies" (Standard / Soft / Firm).

    - Analysis Criteria
        - Politeness (40pts): Correct use of Keigo (Sonkeigo, Kenjougo, Teineigo).
        - Indirectness (30pts): Use of indirect expressions (e.g., ～かと思われます instead of ～です) to soften the tone.
        - Etiquette (30pts): Proper usage of "Cushion Phrases" (Kushion Kotoba) to show respect and distance.

    - Output Rules
        - Provide a Total Score (0-100).
        - Break down the score into the three metrics above.
        - Identify specific cultural/grammatical issues.
        - Suggest 2-3 improved alternatives with "standard" and "highest" levels.

## 3-1. riskLevel 이 재는 것

`riskLevel` 은 **확답을 피하는 정도** 하나만 잰다. 정중도와 무관하며, 무례한 문장도 거절이
아니면 SAFE 다. 둘을 섞어 적었던 동안 모델은 넓은 쪽으로 읽었다 — 평가에서 「よろしく」 가
DANGER, 「明日までにやっといて。」 가 CAUTION 으로 돌아왔다. 둘 다 거절이 아니라 그냥 반말이다.

- SAFE: 확답·수락·요청이거나 다음 단계를 밝힌 경우. 무례함은 등급을 바꾸지 않는다.
- CAUTION: 기한도 약속도 없는 보류. 「検討させていただきます」「考えておきます」
- DANGER: 완곡하더라도 거절. 「難しいですね」「今回は見送らせていただきます」

정중도는 세 지표가 따로 잰다. 한 문장이 "정중한데 거절" 일 수 있기 때문에 축을 나눈다.

### 이 정의가 실제로 얼마나 지켜지는가

라벨된 34문장 중 33문장에 답을 받았고(`model-answers.json`), 그중 **29건이 라벨과 일치**한다.
어긋난 4건은 성격이 다르고, 프롬프트 쪽에서 읽어야 할 것은 두 가지다.

| 문장 | 라벨 | 모델 | 프롬프트가 책임질 부분 |
|---|---|---|---|
| 「申し訳ございませんが、本日中の対応は難しい状況です」 | DANGER | CAUTION | **정의가 안 다룬다.** 거절이 "오늘" 로 한정된 경우가 SAFE·CAUTION·DANGER 중 어디인지 위 세 줄로는 결정되지 않는다 |
| 「社内で確認のうえ、改めてご連絡いたします」 | SAFE | CAUTION | **정의는 분명한데 지켜지지 않았다.** 이 문장은 프롬프트의 SAFE 예시로 적혀 있는데도 CAUTION 이 돌아왔다 |
| 「検討のうえ、必要に応じてご連絡させていただきます」 | CAUTION | DANGER | 정의의 문제가 아니다 — 라벨은 문면(보류), 모델은 실무(거절)를 읽는다 |
| 「状況が変わりましたら、こちらからお声がけいたします」 | CAUTION | DANGER | 같은 차이 |

앞의 두 건은 프롬프트를 고칠 후보다. 다만 고치면 `PROMPT_VERSION` 이 올라가 **받아 둔 33건을
전부 다시 물어야 한다**(무료 한도 하루 20회). 그래서 칸이 다 찬 뒤에 한 번에 판단한다 —
한 문장을 위해 측정을 버리는 쪽이 더 비싸다.

사용자가 보는 등급은 이 판정과 규칙 계층 중 **더 위험한 쪽**이라, 위 일치율이 곧 화면의
정확도는 아니다. 합성한 수치(30/33)는 `ProductGradeTest` 가 매 푸시마다 다시 계산한다.

# 4. Contextual Variables (Input Parameters)
   - user_input: 사용자가 입력한 일본어 문구 — 유저 메시지로 전달
   - relationship_type: INTERNAL (사내), EXTERNAL (사외), INTERVIEW (면접)
     - 유저 메시지의 `# Relationship Context` 로 모델에 전달되고,
       동시에 `AnalysisValidator` 의 리스크 가중치($W$: 1.0 / 1.2 / 1.5)로도 쓰인다
   - communication_channel: SLACK (채팅), EMAIL (이메일), VERBAL (구두)
     - 현재 요청 바디에는 없고, 모델이 `category` 로 역추론한다

# 5. Output JSON Schema
> **스키마는 더 이상 프롬프트에 기술하지 않는다.**
> `NuanceSchemaFactory` 가 `NuanceResponseDTO` record 트리에서 JSON Schema 를 생성하고,
> Gemini 의 `responseSchema` 가 모델 응답이 그 스키마를 지키도록 API 레벨에서 강제한다.
>
> - 스키마 원본: `dto/NuanceResponseDTO.java` 및 그 하위 record 들
> - 필드 의미/허용값: 각 필드의 `@JsonPropertyDescription` 이 그대로 스키마 `description` 으로 전달된다
> - 결과적으로 `required`, `additionalProperties: false` 까지 자동 적용되므로
>   마크다운 코드펜스 제거나 수동 JSON 파싱 방어 코드가 필요 없다
>
> 필드를 추가/변경하려면 **DTO 만 고치면 된다.** 프롬프트와 문서를 동기화할 필요가 없다.
> 런타임 시스템 프롬프트(역할·과업·채점 기준)는 `service/GeminiService.SYSTEM_PROMPT` 에 있다.

## 5-1. 출력 언어 (일본어 필드)
`suggestions[].text` 와 `smartReplies[].content` 는 사용자가 그대로 복사해 보내는 문장이므로
순수 일본어여야 한다. 나머지 설명·피드백·전략 필드는 한국어다.

모델이 이 제약을 어기는 방식은 두 가지로 관측됐다.

1. **괄호 자기 교정** — 외국어 낱말을 쓴 뒤 곧바로 괄호에 올바른 일본어를 덧붙인다.
   예: `お 가르쳐(教えて)いただけますでしょうか`, `何卒よろしく communication(お願い申し上げます)`
2. **낱말 대체** — 일본어 낱말이 들어갈 자리를 외국어가 그냥 차지한다.
   예: `予算や schedule 面で`, `ご dynamic 指定`, `〇〇という形で conditional に調整する`

대응은 두 겹이다.

- **프롬프트** — `# Constraints` 에서 두 방식을 실제 사례와 함께 금지한다(원인).
  외래어는 가타카나로 쓰고 로마자는 약어·고유명사에만 허용한다고 못 박는다.
- **`utils/JapaneseOutputSanitizer`** — 1번은 괄호를 펴서 살리고, 2번은 기계적으로
  되살릴 방법이 없으므로 `AnalysisValidator` 가 그 항목을 목록에서 제외한다(보장).

프롬프트만으로는 보장이 되지 않으므로 두 번째 겹을 둔다. 실제로 프롬프트를 강화한 뒤에도
모델은 여전히 두 방식 모두를 만들어 냈다.

### 근본 원인은 thinking-level 이었다
같은 응답 안에서 한국어 설명 필드와 일본어 문장 필드를 동시에 쓰게 하는 구조라
모델이 문장 중간에 언어를 바꾼다. `gemini.thinking-level` 을 `low` 로 두면 이 코드 스위칭이
심하게 나타난다. 같은 입력 4건으로 측정한 결과는 이렇다.

| `thinking-level` | 혼입으로 제외된 문장 | 살아남은 추천 답장 |
|---|---|---|
| `low` | 9건 | 4 / 12 |
| `high` | 0건 | 12 / 12 |

`low` 에서는 외래어를 `schedule` 처럼 로마자로 적었지만 `high` 에서는 `スケジュール` 로 옳게 쓴다.
그래서 기본값을 `high` 로 둔다(`application.yaml`). 응답 시간이 늘어나는 대가가 있지만,
버려지는 답장이 3개 중 2개였던 것을 감안하면 `low` 는 선택지가 아니다.
`JapaneseOutputSanitizer` 는 이제 상시 교정기가 아니라 드물게 발동하는 안전망이다.

혼입 판정 기준은 **한글이 있거나, 홀로 선 소문자 영단어(3자 이상)가 있는 경우**다.
대문자 약어(`IT`, `PDF`, `URL`)와 고유명사(`Slack`)는 일본어 문장에 정상적으로 등장하므로
통과시키고, URL·메일 주소처럼 식별자를 이루는 토큰도 낱말로 보지 않는다.

제외의 대가로 답장이 3개보다 적게 나올 수 있다. 프론트는 빈 목록까지 감당하며,
어설픈 문장을 그대로 보여 주는 것보다 개수가 줄어드는 편이 낫다고 판단했다.

# 6. Few-Shot Examples (Training the AI)
> 아래 두 예시의 점수는 **저자가 정한 값**이다. 어떤 기준이나 말뭉치에서 가져온 것이 아니라,
> 모델에게 점수대의 감각을 주려고 쓴 보기다. 평가에 쓰는 라벨은 따로 있다 —
> `src/test/resources/evaluation/business-sentences.json` 이며, 행마다 라벨의 근거를 적어 두었다.
> 그쪽도 원어민 검수를 거치지 않았다는 점은 README 의 "What the score is" 에 밝혀 둔다.
    - Example 1
        - Input: "よろしく" (Relationship: External)
        - Analysis: * Total Score: 15/100
            - Metrics: Politeness: 5, Indirectness: 5, Etiquette: 5
        - Feedback: Extremely casual. Lacks any business etiquette or honorifics.
        - Improvement: "よろしくお願いいたします。"

    - Example 2
        - Input: "確認してください" (Relationship: Internal-Superior)
        - Analysis: * Total Score: 45/100
            - Metrics: Politeness: 20, Indirectness: 15, Etiquette: 10
        - Feedback: Uses Teineigo (～してください) but feels like a command. Lacks indirectness.
        - Improvement: "ご確認いただけますでしょうか？"