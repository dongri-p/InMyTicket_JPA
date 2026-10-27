package com.example.dongri.inmyticket.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;

// 목록 조회 API들이 공통으로 쓰는 응답 포맷.
// count: 이번 응답에 담긴 항목 수, totalCount: 페이지네이션과 무관한 전체 항목 수
// (페이지네이션이 없는 API는 count와 totalCount가 항상 같음)
// 커서 방식(공연 목록)은 다음 페이지가 있을 때만 nextCursor를 주고, 전체 건수는 첫 요청에서만 셈(매번 count 쿼리를 돌리지 않으려고)
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ListResult<T> {

    private final int count;
    private final Long totalCount;
    private final String nextCursor;
    private final T data;

    public ListResult(int count, long totalCount, T data) {
        this(count, Long.valueOf(totalCount), null, data);
    }

    private ListResult(int count, Long totalCount, String nextCursor, T data) {
        this.count = count;
        this.totalCount = totalCount;
        this.nextCursor = nextCursor;
        this.data = data;
    }

    public static <T> ListResult<T> withCursor(int count, Long totalCount, String nextCursor, T data) {
        return new ListResult<>(count, totalCount, nextCursor, data);
    }
}
