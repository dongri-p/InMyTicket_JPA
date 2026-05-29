package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.Performance;

import lombok.Getter;

@Getter
public class PerformanceListDto {
    
    private Long id;
    private String title;
    private String category;
    private String status;

    public PerformanceListDto(Performance performance) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.category = performance.getCategory();
        this.status = performance.getStatus();
    }
}
