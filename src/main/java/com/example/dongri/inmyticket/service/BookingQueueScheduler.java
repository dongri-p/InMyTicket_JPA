package com.example.dongri.inmyticket.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 1초마다 대기열 앞사람들을 입장시킴 (운영 중인 대기열이 없으면 아무것도 안 함)
@Component
@RequiredArgsConstructor
public class BookingQueueScheduler {

    private final BookingQueueService bookingQueueService;

    @Scheduled(fixedDelayString = "${booking-queue.admit-interval-ms}")
    public void admit() {
        bookingQueueService.admitAll();
    }
}
