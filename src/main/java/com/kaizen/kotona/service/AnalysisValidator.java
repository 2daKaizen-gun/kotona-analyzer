package com.kaizen.kotona.service;

import com.kaizen.kotona.dto.MetricsDTO;
import com.kaizen.kotona.dto.NuanceResponseDTO;
import com.kaizen.kotona.utils.EtiquetteConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AnalysisValidator {
    //AI 점수와 형태소 분석 및 규칙 기반 데이터 교차 검증
    public NuanceResponseDTO validate(NuanceResponseDTO aiResponse, String cleanInput, boolean hasPoliteEnding) {
        // 기존 Metrics 가져오기
        int p = aiResponse.metrics().politeness();   // Max 40
        int i = aiResponse.metrics().indirectness(); // Max 30
        int e = aiResponse.metrics().etiquette();    // Max 30

        // 1. Politeness 검증 (경어 사용 여부)
        if (p >= 30 && !hasPoliteEnding) {
            log.warn("검증 경고: 경어 점수는 높으나 です/ます 토큰이 발견되지 않음.");
            p = Math.max(0, p - 10); // 10점 페널티
        }

        // 2. Etiquette 검증 (쿠션어 사용 여부)
        boolean hasCushion = EtiquetteConstants.CUSHION_PHRASES.stream()
                .anyMatch(cleanInput::contains);
        if (e >= 20 && !hasCushion) {
            log.warn("검증 경고: 에티켓 점수는 높으나 주요 쿠션어가 발견되지 않음.");
            e = Math.max(0, e - 10); // 10점 페널티
        }

        // 3. Indirectness 검증 (완곡 어미 사용 여부)
        boolean hasIndirect = EtiquetteConstants.INDIRECT_ENDINGS.stream()
                .anyMatch(cleanInput::contains);
        if (i >= 20 && !hasIndirect) {
            log.warn("검증 경고: 간접화법 점수는 높으나 완곡 어미가 발견되지 않음.");
            i = Math.max(0, i - 5); // 5점 페널티
        }

        // 최종 총점 재계산 (합계가 100을 넘지 않도록 보정)
        int finalTotal = Math.min(100, p + i + e);

        // 최종적으로 조정된 값 담은 DTO 반환
        return new NuanceResponseDTO(
                finalTotal,
                new MetricsDTO(p, i, e),
                aiResponse.evaluation(),
                aiResponse.feedback(),
                aiResponse.suggestions(),
                aiResponse.sentiment()
        );
    }
}