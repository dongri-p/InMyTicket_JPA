package com.example.dongri.inmyticket.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 예매/결제/회차 등록 API가 공통으로 쓰는 "생성된 리소스 id + 안내 메시지" 응답 형태
@Getter
@AllArgsConstructor
public class CreateResourceResponse {

    private Long id;
    private String message;

}
