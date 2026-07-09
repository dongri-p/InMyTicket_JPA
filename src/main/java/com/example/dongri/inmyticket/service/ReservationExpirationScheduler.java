package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 결제 없이 방치된 PENDING 예약의 좌석을 주기적으로 회수
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationScheduler {

    private final ReservationService reservationService;

    @Value("${reservation.pending-timeout-minutes}")
    private long pendingTimeoutMinutes;

    @Scheduled(fixedDelayString = "${reservation.expiration-check-interval-ms}")
    public void releaseExpiredPendingReservations() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingTimeoutMinutes);
        int expiredCount = reservationService.expireStalePendingReservations(cutoff);
        if (expiredCount > 0) {
            log.info("결제 미완료로 {}건의 예약을 자동 취소했습니다. (기준 시각: {})", expiredCount, cutoff);
        }
    }
}
