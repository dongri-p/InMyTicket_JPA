package com.example.dongri.inmyticket.domain;

// 본인 소유가 아닌 리소스에 접근/조작하려 할 때 도메인 계층에서 던지는 예외.
// Spring Security의 AccessDeniedException에 의존하면 도메인이 웹/보안 프레임워크를 알게 되므로,
// 프레임워크 의존 없는 순수 RuntimeException으로 분리했다. HTTP 403 매핑은 GlobalExceptionHandler가 담당한다.
public class OwnershipViolationException extends RuntimeException {

    public OwnershipViolationException(String message) {
        super(message);
    }
}
