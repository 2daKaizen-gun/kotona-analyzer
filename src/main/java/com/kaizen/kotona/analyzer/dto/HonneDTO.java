package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record HonneDTO(
        @JsonPropertyDescription("겉으로 드러난 표면적 의미(建前). 한국어로 작성한다.")
        String tatemae,

        @JsonPropertyDescription("숨겨진 실제 의도(本音). 한국어로 작성한다.")
        String trueIntent,

        @JsonPropertyDescription("사용자가 취해야 할 권장 행동. 한국어로 작성한다.")
        String actionItem
) {}
