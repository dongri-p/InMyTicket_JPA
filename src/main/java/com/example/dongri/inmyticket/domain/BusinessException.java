package com.example.dongri.inmyticket.domain;

// 비즈니스 규칙 위반을 ErrorCode로 표현하는 예외. HTTP 응답 매핑은 GlobalExceptionHandler가 담당한다.
// 스프링 예외에 의존하지 않는 순수 RuntimeException이라 도메인·서비스 어디서든 던질 수 있음
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    // 사용자에게 보여줄 문구를 상황에 맞게 바꿀 때 (예: PG가 내려준 거절 사유, 관리자 회차 등록 검증)
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
