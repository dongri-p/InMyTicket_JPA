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

    public ScheduleListDto(Schedule schedule) {
        this.scheduleId = schedule.getId();
        this.startTime = schedule.getStartTime();
        this.totalSeatCount = schedule.getTotalSeatCount();
        this.availableSeatCount = schedule.getAvailableSeatCount();
    }
}
