package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;

import com.example.dongri.inmyticket.domain.Performance;

import lombok.Getter;

@Getter
public class PerformanceDetailDto {
    private Long id;
    private String title;
    private String category;
    private String status;
    private String artist;
    private String description;
    private LocalDateTime lastUpdatedAt;

    public PerformanceDetailDto(Performance performance) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.category = performance.getCategory();
        this.status = performance.getStatus();
        this.artist = performance.getArtist();
        this.description = performance.getDescription();
        this.lastUpdatedAt = performance.getLastUpdatedAt();
    }
}