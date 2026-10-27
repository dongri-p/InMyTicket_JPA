package com.example.dongri.inmyticket.domain;

// URL이 가리키는 리소스(공연/회차/예약)가 없을 때 던지는 예외. GlobalExceptionHandler가 HTTP 404로 매핑한다.
// 요청 본문 안의 id가 잘못된 경우(예: 회차 등록 시 없는 공연장)는 요청 내용 오류라 IllegalArgumentException(400)을 그대로 쓴다.
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
