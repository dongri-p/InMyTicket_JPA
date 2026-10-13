package com.example.dongri.inmyticket.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
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

    private LocalDateTime lastUpdatedAt;
    
}
