package com.kaizen.kotona.service;

import com.kaizen.kotona.dto.NuanceResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class AnalysisValidator {
    /**
     * AI 점수와 형태소 분석 결과를 교차 검증합니다.
     * @param aiResponse Gemini가 분석한 결과 DTO
     * @param hasPoliteEnding Kuromoji가 판단한 정중어 포함 여부
     * @return 최종 신뢰도 점수 또는 조정된 DTO
     */
    public NuanceResponseDTO validate(NuanceResponseDTO aiResponse, boolean hasPoliteEnding) {
        int finalScore = aiResponse.score();
        // 검증 로직 예시: AI 점수는 높으나(ex 8 이상), 실제 문장에 '입니다/합니다'가 없는 경우
        if(finalScore >= 8 && !hasPoliteEnding) {
            log.warn("검증 경고: AI 점수는 높으나 정중어(です/ます) 토큰이 발견되지 않음.");
            // 신뢰도 점수를 조정하거나 피드백에 경고 문구를 추가
            // 예시로 점수를 1점 감점하고 이슈에 추가
            finalScore = Math.max(1, finalScore-1);
        }

        log.info("최종 검증 완료 - 조정된 점수: {}", finalScore-1);

        // 최종적으로 조정된 값 담은 DTO 반환
        return new NuanceResponseDTO(
                finalScore,
                aiResponse.evaluation(),
                aiResponse.feedback(),
                aiResponse.suggestions()
        );
    }
}