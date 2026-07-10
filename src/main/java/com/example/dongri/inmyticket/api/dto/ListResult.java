package com.example.dongri.inmyticket.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 목록 조회 API들이 공통으로 쓰는 응답 포맷.
// count: 이번 응답에 담긴 항목 수, totalCount: 페이지네이션과 무관한 전체 항목 수
// (페이지네이션이 없는 API는 count와 totalCount가 항상 같음)
@Getter
@AllArgsConstructor
public class ListResult<T> {

    private int count;
    private long totalCount;
    private T data;
}
