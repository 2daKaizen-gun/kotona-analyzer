package com.kaizen.kotona.analyzer.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지 응답. Spring Data 의 Page 를 그대로 직렬화하지 않는다 —
 * 그쪽 JSON 구조는 버전에 따라 바뀌고, 클라이언트가 쓰지 않는 필드가 많다.
 */
public record PageResponse<T>(
        @JsonPropertyDescription("이번 페이지의 항목들")
        List<T> content,

        @JsonPropertyDescription("0부터 시작하는 페이지 번호")
        int page,

        @JsonPropertyDescription("페이지 크기")
        int size,

        @JsonPropertyDescription("전체 항목 수")
        long totalElements,

        @JsonPropertyDescription("전체 페이지 수")
        int totalPages,

        @JsonPropertyDescription("다음 페이지 존재 여부")
        boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }
}
