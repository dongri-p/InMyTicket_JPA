package com.example.dongri.inmyticket.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateScheduleRequest;
import com.example.dongri.inmyticket.api.dto.ScheduleListDto;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.service.ScheduleService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScheduleApiController {
    
    private final ScheduleService scheduleService;

    // 관리자 기능. 공연 회차 등록 API
    @PostMapping("/api/v1/schedules")
    public CreateScheduleResponse saveSchedule(@RequestBody CreateScheduleRequest request) {
        Long id = scheduleService.saveSchedule(
                request.getPerformanceId(),
                request.getHallId(),
                request.getStartTime(),
                request.getTotalSeatCount()
        );
        return new CreateScheduleResponse(id, "공연 회차 및 좌석 생성이 완료되었습니다.");
    }

    // 특정 공연의 모든 회차 일정 조회 API
    @GetMapping("/api/v1/performances/{performanceId}/schedules")
    public Result schedulesV2(@PathVariable("performanceId") Long performanceId) {
        List<Schedule> findSchedules = scheduleService.findSchedulesByPerformance(performanceId);

        List<ScheduleListDto> collect = findSchedules.stream()
                .map(ScheduleListDto::new)
                .collect(Collectors.toList());

        return new Result(collect.size(), collect);
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private int count;
        private T data;
    }

    @Data
    @AllArgsConstructor
    static class CreateScheduleResponse {
        private Long scheduleId;
        private String message;
    }
}
