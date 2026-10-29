package com.example.dongri.inmyticket.api;

import java.time.LocalDateTime;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.QueueStatusDto;
import com.example.dongri.inmyticket.config.AuthenticatedMember;
import com.example.dongri.inmyticket.service.BookingQueueService;
import com.example.dongri.inmyticket.service.ScheduleService;

import lombok.RequiredArgsConstructor;

// 회차 예매 대기열 - 로그인한 회원만 (순번은 회원 기준)
@RestController
@RequiredArgsConstructor
public class BookingQueueApiController {

    private final BookingQueueService bookingQueueService;
    private final ScheduleService scheduleService;

    // 줄 서기 (이미 서 있으면 순번 유지)
    @PostMapping("/api/v1/schedules/{scheduleId}/queue")
    public QueueStatusDto join(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long scheduleId) {

        return bookingQueueService.join(scheduleService.findSchedule(scheduleId),
                authenticatedMember.getMemberId(), LocalDateTime.now());
    }

    // 내 순번·입장 여부 (대기 화면이 몇 초마다 물어봄)
    @GetMapping("/api/v1/schedules/{scheduleId}/queue/me")
    public QueueStatusDto myStatus(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long scheduleId) {

        return bookingQueueService.status(scheduleService.findSchedule(scheduleId),
                authenticatedMember.getMemberId(), LocalDateTime.now());
    }
}
