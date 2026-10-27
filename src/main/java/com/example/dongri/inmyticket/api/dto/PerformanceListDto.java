package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDate;

import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.Performance;

import lombok.Getter;

@Getter
public class PerformanceListDto {
    
    private Long id;
    private String title;
    private String category;
    private String status;
    private String posterUrl;
    // 공연 기간 (KOPIS에 기간이 없으면 null). 관리자 회차 등록 폼이 날짜 선택 범위를 제한하는 데 사용
    private LocalDate startDate;
    private LocalDate endDate;
    // 아직 시작 전인 회차 수
    private long upcomingScheduleCount;
    // 예매 상태 (ON_SALE 예매중 / UPCOMING 오픈예정 / ENDED 공연종료). 목록 필터 ?status=와 같은 값
    private BookingStatus bookingStatus;

    public PerformanceListDto(Performance performance, long upcomingScheduleCount) {
        this.id = performance.getId();
        this.title = performance.getTitle();
        this.category = performance.getCategory();
        this.status = performance.getStatus();
        this.posterUrl = performance.getPosterUrl();
        this.startDate = performance.getStartDate();
        this.endDate = performance.getEndDate();
        this.upcomingScheduleCount = upcomingScheduleCount;
        this.bookingStatus = BookingStatus.of(upcomingScheduleCount, performance.getEndDate(), LocalDate.now());
    }
}
