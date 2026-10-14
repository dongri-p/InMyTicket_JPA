package com.example.dongri.inmyticket.domain;

// 공연 목록의 예매 상태 필터(?status=). 목록 카드 배지와 같은 기준이고, code는 목록 정렬 순서와 같음
// (PerformanceRepository.BOOKING_GROUP이 계산하는 값: 0 예매중, 1 오픈예정, 2 공연종료)
public enum BookingStatus {

    // 아직 시작 전인 회차가 있음
    ON_SALE(0),
    // 예매 가능한 회차는 없지만 종료일이 오늘 이후거나 기간을 모름
    UPCOMING(1),
    // 종료일이 오늘보다 이전
    ENDED(2);

    private final int code;

    BookingStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
