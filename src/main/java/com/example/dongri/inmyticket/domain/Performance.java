package com.example.dongri.inmyticket.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Performance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long id;

    @Column(unique = true)
    private String apiId;
    private String title;
    private String artist;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;
    private String status;
    private String posterUrl;

    // KOPIS 공연 기간(prfpdfrom~prfpdto)과 공연장명(fcltynm). 회차 등록 시 기간 밖 날짜를 막는 기준으로 사용.
    // V7 이전에 저장된 공연은 비어 있을 수 있음(기동 시 backfill로 채움)
    private LocalDate startDate;
    private LocalDate endDate;
    private String venueName;

    // KOPIS 공연 지역(시·도, 예: 서울특별시). 목록 지역 필터(RegionGroup)의 기준. V8 이전 공연은 backfill로 채움
    private String area;

    private LocalDateTime lastUpdatedAt;

    // 가장 늦은 회차 시각 (회차가 없으면 null). 이 시각이 지나면 예매중에서 내려감
    private LocalDateTime lastScheduleAt;

    // 목록 정렬·필터용 예매 상태 그룹 (BookingStatus.code: 0 예매중 / 1 오픈예정 / 2 공연종료).
    // 요청마다 회차를 뒤져 계산하면 인덱스를 못 타서 컬럼으로 저장. 회차 등록 시 바로 갱신하고,
    // 시간이 지나 바뀌는 경우(마지막 회차 시작, 종료일 경과)는 BookingGroupScheduler가 주기적으로 내려 줌
    private int bookingGroup = BookingStatus.UPCOMING.getCode();

    // 새 공연은 회차가 없으므로 종료일만 보고 그룹을 정함
    @PrePersist
    void initBookingGroup() {
        refreshBookingGroup(LocalDateTime.now());
    }

    // 회차가 추가되면 마지막 회차 시각과 그룹을 갱신
    public void scheduleAdded(LocalDateTime startTime, LocalDateTime now) {
        if (lastScheduleAt == null || startTime.isAfter(lastScheduleAt)) {
            lastScheduleAt = startTime;
        }
        refreshBookingGroup(now);
    }

    public void refreshBookingGroup(LocalDateTime now) {
        long upcoming = lastScheduleAt != null && lastScheduleAt.isAfter(now) ? 1 : 0;
        bookingGroup = BookingStatus.of(upcoming, endDate, now.toLocalDate()).getCode();
    }
    
}
