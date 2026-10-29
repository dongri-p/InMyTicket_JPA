package com.example.dongri.inmyticket.domain;

// API 에러 코드. 화면은 message 문구 대신 code로 분기한다 (같은 409라도 '이미 예매된 좌석'과 '결제 진행 중'은 다른 대응이 필요).
// status는 HTTP 상태 코드 숫자 (도메인이 스프링 HttpStatus를 알지 않도록 int로 둠)
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(400, "요청 형식이 올바르지 않습니다."),
    INVALID_CURSOR(400, "커서 형식이 올바르지 않습니다."),
    UNAUTHORIZED(401, "인증이 필요합니다."),
    FORBIDDEN(403, "접근 권한이 없습니다."),
    PATH_NOT_FOUND(404, "요청한 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(405, "지원하지 않는 요청 방식입니다."),
    DUPLICATE_VALUE(409, "이미 사용 중인 값입니다."),
    CONCURRENT_MODIFICATION(409, "다른 요청이 먼저 처리되었습니다. 다시 시도해주세요."),
    INTERNAL_ERROR(500, "서버 내부 오류가 발생했습니다."),

    // 회원
    LOGIN_FAILED(401, "아이디 또는 비밀번호가 일치하지 않습니다."),
    LOGIN_LOCKED(429, "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."),
    DUPLICATE_LOGIN_ID(409, "이미 사용 중인 아이디입니다."),
    DUPLICATE_EMAIL(409, "이미 사용 중인 이메일입니다."),
    MEMBER_NOT_FOUND(404, "회원 정보를 찾을 수 없습니다."),

    // 공연·회차 (URL이 가리키는 대상이 없으면 404)
    PERFORMANCE_NOT_FOUND(404, "존재하지 않는 공연입니다."),
    SCHEDULE_NOT_FOUND(404, "존재하지 않는 회차입니다."),
    HALL_NOT_FOUND(404, "존재하지 않는 공연장입니다."),
    INVALID_SCHEDULE(400, "회차를 등록할 수 없습니다."),

    // 예매
    RESERVATION_NOT_FOUND(404, "존재하지 않는 예약입니다."),
    NOT_RESERVATION_OWNER(403, "본인의 예약만 처리할 수 있습니다."),
    QUEUE_ENTRY_REQUIRED(403, "대기열에서 입장 순서가 되어야 예매할 수 있습니다."),
    DUPLICATE_SEAT_SELECTION(400, "같은 좌석이 중복으로 선택되었습니다."),
    SEAT_NOT_FOUND(400, "존재하지 않는 좌석이 포함되어 있습니다."),
    MIXED_SCHEDULE_SEATS(400, "같은 회차의 좌석만 함께 예매할 수 있습니다."),
    SEAT_ALREADY_RESERVED(409, "이미 예매 완료된 좌석입니다."),
    SEAT_NOT_RESERVED(409, "예매되지 않은 좌석은 취소할 수 없습니다."),
    SHOW_ALREADY_STARTED(409, "공연이 이미 시작되었습니다."),
    BOOKING_NOT_OPEN(409, "아직 예매 오픈 전입니다."),
    RESERVATION_ALREADY_CANCELLED(409, "이미 취소된 예약입니다."),

    // 결제
    PAYMENT_NOT_FOUND(404, "존재하지 않는 결제입니다."),
    RESERVATION_NOT_PAYABLE(409, "결제할 수 없는 예약 상태입니다."),
    PAYMENT_ALREADY_PROCESSING(409, "이 예약의 결제가 이미 진행 중입니다. 잠시 후 결과를 확인해주세요."),
    PAYMENT_KEY_REUSED(409, "이미 다른 결제에 사용된 결제 키입니다."),
    PAYMENT_IN_PROGRESS(409, "결제가 진행 중이라 취소할 수 없습니다. 잠시 후 다시 시도해주세요."),
    PAYMENT_STATE_CHANGED(409, "결제 상태가 변경되었습니다. 취소를 다시 시도해주세요."),
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 예약 금액과 다릅니다."),
    ORDER_ID_MISMATCH(400, "이 예약의 주문번호가 아닙니다."),
    UNSUPPORTED_PAYMENT_PROVIDER(400, "지원하지 않는 결제 수단입니다."),
    PAYMENT_REJECTED(400, "결제에 실패했습니다."),
    QUEUE_UNAVAILABLE(503, "대기열을 확인할 수 없습니다. 잠시 뒤 다시 시도해 주세요."),
    PG_UNAVAILABLE(502, "결제 서버와 통신 중 오류가 발생했습니다.");

    private final int status;
    private final String message;

    ErrorCode(int status, String message) {
        this.status = status;
        this.message = message;
    }

    public int getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
