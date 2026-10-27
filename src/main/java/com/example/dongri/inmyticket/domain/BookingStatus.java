package com.example.dongri.inmyticket.domain;

import java.time.LocalDate;

// 공연 목록의 예매 상태 필터(?status=)이자 공연 응답의 bookingStatus 값. code는 목록 정렬 순서와 같음
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

    // 응답에 내려줄 예매 상태 판정. PerformanceRepository.BOOKING_GROUP(필터·정렬)과 같은 규칙이라 한쪽을 바꾸면 다른 쪽도 같이 바꿀 것
    public static BookingStatus of(long upcomingScheduleCount, LocalDate endDate, LocalDate today) {
        if (upcomingScheduleCount > 0) {
            return ON_SALE;
        }
        if (endDate == null || !endDate.isBefore(today)) {
            return UPCOMING;
        }
        return ENDED;
    }
}
