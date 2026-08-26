package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record SmartReplyDTO(
   @JsonPropertyDescription("답장 시나리오. \"Clarification\", \"Soft Acceptance\", \"Counter-proposal\" 중 하나.")
   String scenario,

   @JsonPropertyDescription("실제로 보낼 수 있는 정중한 답장 문구. 반드시 일본어로 작성한다.")
   String content,

   @JsonPropertyDescription("이 답장의 의도와 기대 효과. 한국어로 작성한다.")
   String description,

   @JsonPropertyDescription("어조의 강도. \"Standard\", \"Soft\", \"Firm\" 중 하나.")
   String nuanceLevel
) {}
