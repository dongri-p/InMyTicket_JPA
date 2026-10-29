package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;

import com.example.dongri.inmyticket.domain.GradeSection;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateScheduleRequest {

    @NotNull
    private Long performanceId;

    @NotNull
    private Long hallId;

    @NotNull
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @Min(1)
    @Max(100000)
    private int totalSeatCount;

    // 예매 오픈 시각 (선택). 비우면 등록하자마자 예매 가능
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Seoul")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime bookingOpenAt;

    // 무대에서 가까운 구역부터 등급별 좌석 수·가격 (선택). 비우면 기본 배치 VIP 20%·R 30%·S 30%·A 나머지
    @Valid
    @Size(max = 4)
    private List<GradeSectionRequest> grades;

    public List<GradeSection> toSections() {
        return grades == null ? null : grades.stream().map(GradeSectionRequest::toSection).collect(Collectors.toList());
    }
}
