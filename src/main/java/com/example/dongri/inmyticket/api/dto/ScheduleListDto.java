package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;

import com.example.dongri.inmyticket.domain.Schedule;

import lombok.Getter;

@Getter
public class ScheduleListDto {
    
    private Long scheduleId;
    private LocalDateTime startTime;
    private int totalSeatCount;
    private int availableSeatCount;

    // 잔여석은 회차에 저장하지 않고 좌석 상태에서 세서 따로 받음 (ScheduleService.countAvailableSeats)
    public ScheduleListDto(Schedule schedule, int availableSeatCount) {
        this.scheduleId = schedule.getId();
        this.startTime = schedule.getStartTime();
        this.totalSeatCount = schedule.getTotalSeatCount();
        this.availableSeatCount = availableSeatCount;
    }
}
