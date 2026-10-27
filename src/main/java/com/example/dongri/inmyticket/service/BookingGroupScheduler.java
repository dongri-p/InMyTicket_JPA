package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.repository.PerformanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시간이 지나면서 바뀌는 공연 예매 상태 그룹(Performance.bookingGroup)을 주기적으로 내려 줌
// - 예매중 -> 마지막 회차가 시작되면 오픈예정/공연종료
// - 오픈예정 -> 종료일이 지나면 공연종료
// 올라가는 경우(회차 등록 -> 예매중)는 ScheduleService가 등록 시점에 바로 반영함.
// 그래서 목록의 예매 상태는 최대 한 주기(기본 1분) 늦을 수 있음 — 예매 자체는 회차 시각을 따로 검사하므로 지난 회차가 팔리지는 않음
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingGroupScheduler {

    private final PerformanceRepository performanceRepository;

    @Scheduled(fixedDelayString = "${performance.booking-group-refresh-interval-ms}")
    @Transactional
    public void refreshBookingGroups() {
        LocalDateTime now = LocalDateTime.now();
        int finished = performanceRepository.demoteFinishedOnSale(now, now.toLocalDate());
        int ended = performanceRepository.demoteEndedUpcoming(now.toLocalDate());
        if (finished + ended > 0) {
            log.info("공연 예매 상태 갱신: 예매중 종료 {}건, 공연종료 {}건", finished, ended);
        }
    }
}
