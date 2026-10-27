package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.Performance;

import lombok.Getter;

@Getter
public class PerformanceDetailDto {
    private Long id;
    private String title;
    private String category;
    private String posterUrl;
    private LocalDate startDate;
    private LocalDate endDate;
    private String venueName;
    private String artist;
    private String description;
    private LocalDateTime lastUpdatedAt;
    // 예매 상태 (목록 응답·필터와 같은 값)
    private BookingStatus bookingStatus;

    public PerformanceDetailDto(Performance performance, long upcomingScheduleCount) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.category = performance.getCategory();
        this.posterUrl = performance.getPosterUrl();
        this.startDate = performance.getStartDate();
        this.endDate = performance.getEndDate();
        this.venueName = performance.getVenueName();
        this.artist = performance.getArtist();
        this.description = performance.getDescription();
        this.lastUpdatedAt = performance.getLastUpdatedAt();
        this.bookingStatus = BookingStatus.of(upcomingScheduleCount, performance.getEndDate(), LocalDate.now());
    }
}