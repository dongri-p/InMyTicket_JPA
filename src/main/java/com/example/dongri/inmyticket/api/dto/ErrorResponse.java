package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.ErrorCode;

import lombok.Getter;

// 모든 에러 응답의 공통 형태: {status, code, message}
// code는 화면이 분기할 때 쓰는 값(문구가 바뀌어도 유지), message는 사용자에게 보여줄 문구
@Getter
public class ErrorResponse {

    private final int status;
    private final String code;
    private final String message;

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode, errorCode.getMessage());
    }

    public ErrorResponse(ErrorCode errorCode, String message) {
        this.status = errorCode.getStatus();
        this.code = errorCode.name();
        this.message = message;
    }
}
