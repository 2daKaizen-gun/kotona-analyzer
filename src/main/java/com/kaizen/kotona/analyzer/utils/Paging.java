package com.kaizen.kotona.analyzer.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 목록 API 의 페이지 요청을 한 곳에서 만든다.
 *
 * <p>상한·하한을 엔드포인트마다 따로 두면 조용히 갈라진다. 프론트는 두 목록이 같은
 * 규칙으로 동작한다고 가정하므로(예시 데이터도 그 가정 위에 있다), 여기 하나만 둔다.
 */
public final class Paging {

    /** 한 번에 가져갈 수 있는 최대 건수. 클라이언트가 더 요청해도 이보다 크게는 주지 않는다. */
    public static final int MAX_PAGE_SIZE = 100;

    public static final int DEFAULT_PAGE_SIZE = 20;

    private Paging() {
    }

    public static PageRequest of(int page, int size, Sort sort) {
        return PageRequest.of(
                Math.max(0, page),
                Math.min(MAX_PAGE_SIZE, Math.max(1, size)),
                sort);
    }
}
