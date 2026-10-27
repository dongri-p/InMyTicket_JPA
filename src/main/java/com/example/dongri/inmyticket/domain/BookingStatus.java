package com.example.dongri.inmyticket.domain;

import java.time.LocalDate;

// 공연 목록의 예매 상태 필터(?status=)이자 공연 응답의 bookingStatus 값. code는 목록 정렬 순서와 같음
// (Performance.bookingGroup에 저장되는 값: 0 예매중, 1 오픈예정, 2 공연종료)
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

    // 저장된 그룹 값(Performance.bookingGroup) -> 상태
    public static BookingStatus ofCode(int code) {
        for (BookingStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("알 수 없는 예매 상태 코드: " + code);
    }

    // 예매 상태 판정 규칙. Performance.refreshBookingGroup(저장값)과 상세 응답(실시간)이 함께 씀
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
