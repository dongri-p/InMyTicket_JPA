package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.BookingStatus;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

// 응답의 bookingStatus 판정 규칙 (PerformanceRepository.BOOKING_GROUP과 같은 경계값)
public class BookingStatusTest {

    private final LocalDate today = LocalDate.of(2026, 10, 27);

    @Test
    @DisplayName("시작 전 회차가 있으면 종료일과 상관없이 예매중이다")
    void upcomingSchedule_onSale() {
        Assertions.assertEquals(BookingStatus.ON_SALE, BookingStatus.of(1, today.minusDays(1), today));
    }

    @Test
    @DisplayName("회차가 없고 기간을 모르면 오픈예정이다")
    void noSchedule_unknownPeriod_upcoming() {
        Assertions.assertEquals(BookingStatus.UPCOMING, BookingStatus.of(0, null, today));
    }

    @Test
    @DisplayName("오늘 끝나는 공연은 아직 공연종료가 아니다")
    void endsToday_upcoming() {
        Assertions.assertEquals(BookingStatus.UPCOMING, BookingStatus.of(0, today, today));
    }

    @Test
    @DisplayName("종료일이 어제면 공연종료다")
    void endedYesterday_ended() {
        Assertions.assertEquals(BookingStatus.ENDED, BookingStatus.of(0, today.minusDays(1), today));
    }
}
