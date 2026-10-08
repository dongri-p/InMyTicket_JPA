package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.Performance;

import lombok.Getter;

@Getter
public class PerformanceListDto {
    
    private Long id;
    private String title;
    private String category;
    private String status;
    private String posterUrl;
    // 아직 시작 전인 회차 수. 0이면 목록에서 '오픈예정', 1 이상이면 '예매중'으로 표시
    private long upcomingScheduleCount;

    public PerformanceListDto(Performance performance, long upcomingScheduleCount) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.category = performance.getCategory();
        this.status = performance.getStatus();
        this.posterUrl = performance.getPosterUrl();
        this.upcomingScheduleCount = upcomingScheduleCount;
    }
}
