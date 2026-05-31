package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class CreateScheduleRequest {
    
    private Long performanceId;
    private Long hallId;

    @JsonFormat(pattern = "yyyy-mm-dd HH:mm:ss")
    private LocalDateTime startTime;

    private int totalSeatCount;
}
