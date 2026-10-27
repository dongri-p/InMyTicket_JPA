package com.example.dongri.inmyticket.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.dongri.inmyticket.api.dto.ErrorResponse;
import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;

// 예외 -> {status, code, message} 응답 변환을 한 곳에서 담당.
// 비즈니스 규칙 위반은 BusinessException의 ErrorCode가 상태 코드까지 정하고, 나머지는 스프링·DB 예외를 코드로 옮김
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 좌석 선점, 결제 상태, 소유자 확인 등 서비스·도메인이 던지는 규칙 위반
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        if (errorCode.getStatus() >= 500) {
            log.error("서버 오류 [{}] {}", errorCode, e.getMessage(), e);
        }
        return respond(errorCode, e.getMessage());
    }

    // @NotBlank, @NotNull, @Email 등 validation 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = (fieldError != null) ? fieldError.getDefaultMessage() : ErrorCode.INVALID_REQUEST.getMessage();
        return respond(ErrorCode.INVALID_REQUEST, message);
    }

    // 쿼리 파라미터 타입 불일치 (예: size=abc, status=SOLD_OUT) - 클라이언트 입력 오류이므로 500이 아닌 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return respond(ErrorCode.INVALID_REQUEST, "요청 파라미터 형식이 올바르지 않습니다: " + e.getName());
    }

    // 요청 본문이 JSON으로 파싱 불가능한 경우 (예: 깨진 JSON)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        return respond(ErrorCode.INVALID_REQUEST, "요청 본문의 형식이 올바르지 않습니다.");
    }

    // loginId/email/paymentKey 등 DB unique 제약 위반 (동시 요청으로 앱 레벨 중복 검사를 통과한 경우)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(DataIntegrityViolationException e) {
        return respond(ErrorCode.DUPLICATE_VALUE, ErrorCode.DUPLICATE_VALUE.getMessage());
    }

    // @Version 충돌 - 다른 요청이 같은 행을 먼저 바꿈
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        return respond(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.getMessage());
    }

    // 없는 경로 (예: /api/v1/reservations/) - 아래 Exception 핸들러에 잡혀 500이 되지 않도록 404로
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException e) {
        return respond(ErrorCode.PATH_NOT_FOUND, ErrorCode.PATH_NOT_FOUND.getMessage());
    }

    // 경로는 있지만 메서드가 다름 (예: 예전 DELETE 취소 URL) - 500이 아닌 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return respond(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage() + " " + e.getMethod());
    }

    // 위에서 다루지 않은 예상치 못한 예외 - 내부 정보를 노출하지 않고 표준 포맷으로 응답
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return respond(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ErrorResponse> respond(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus()).body(new ErrorResponse(errorCode, message));
    }
}
