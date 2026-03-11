package com.kaizen.kotona.analyzer.dto;

public record SmartReplyDTO(
   String scenario, // 수락(Accept), 거절(Refuse), 확인(Clarification) 등
   String content, // 실제 일본어 답장 문구
   String description, // 답장을 썼을 때의 기대 효과 (Korean)
   String nuanceLevel // Standard / Soft / Firm (강도)
) {}
