package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

// 대기열에서 내 상태. 화면은 state에 따라 카운트다운 / 대기 화면 / 좌석 선택을 보여줌
// - NOT_OPEN: 예매 오픈 전 (bookingOpenAt)
// - NO_QUEUE: 대기열 없이 바로 예매 (오픈 시각이 없거나 대기열 운영 시간이 지남)
// - NOT_IN_QUEUE: 대기열 운영 중인데 아직 줄을 안 섰음 (또는 입장 시간이 지남)
// - WAITING: 줄 서는 중 (position번째, 대략 estimatedWaitSeconds초 뒤 입장)
// - ADMITTED: 입장함, admissionExpiresInSeconds초 안에 예매
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueueStatusDto(String state, LocalDateTime bookingOpenAt, Long position,
                             Long estimatedWaitSeconds, Long admissionExpiresInSeconds) {

    public static QueueStatusDto notOpen(LocalDateTime bookingOpenAt) {
        return new QueueStatusDto("NOT_OPEN", bookingOpenAt, null, null, null);
    }

    public static QueueStatusDto noQueue() {
        return new QueueStatusDto("NO_QUEUE", null, null, null, null);
    }

    public static QueueStatusDto notInQueue() {
        return new QueueStatusDto("NOT_IN_QUEUE", null, null, null, null);
    }

    public static QueueStatusDto waiting(long position, long estimatedWaitSeconds) {
        return new QueueStatusDto("WAITING", null, position, estimatedWaitSeconds, null);
    }

    public static QueueStatusDto admitted(long admissionExpiresInSeconds) {
        return new QueueStatusDto("ADMITTED", null, null, null, admissionExpiresInSeconds);
    }
}
