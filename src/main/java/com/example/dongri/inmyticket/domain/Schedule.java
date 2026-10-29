package com.example.dongri.inmyticket.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Schedule {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performance_id")
    private Performance performance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hall_id")
    private Hall hall;

    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL)
    private List<Seat> seats = new ArrayList<>();

    private LocalDateTime startTime;
    private int totalSeatCount;

    // 연관관계 편의 메서드
    public void addSeat(Seat seat) {
        this.seats.add(seat);
        seat.setSchedule(this);
    }

    // 등급을 따로 정하지 않은 회차 - 기본 배치(GradeSection.defaults)로 좌석 생성
    public static Schedule createSchedule(Performance performance, Hall hall, LocalDateTime starTime, int totalSeatCount) {
        return createSchedule(performance, hall, starTime, GradeSection.defaults(totalSeatCount));
    }

    // 공연 회차 생성 시 좌석까지 만듦. 구역 목록 순서대로 1번 좌석부터 이어서 등급·가격을 붙임
    // (예: VIP 4석, R 6석이면 1~4번 VIP, 5~10번 R)
    public static Schedule createSchedule(Performance performance, Hall hall, LocalDateTime starTime, List<GradeSection> sections) {
        Schedule schedule = new Schedule();
        schedule.setPerformance(performance);
        schedule.setHall(hall);
        schedule.setStartTime(starTime);
        schedule.setTotalSeatCount(sections.stream().mapToInt(GradeSection::seatCount).sum());

        int seatNumber = 1;
        for (GradeSection section : sections) {
            for (int i = 0; i < section.seatCount(); i++) {
                Seat seat = new Seat();
                seat.setSeatNumber(seatNumber++);
                seat.setGrade(section.grade());
                seat.setPrice(section.price());
                seat.setStatus(SeatStatus.AVAILABLE);

                schedule.addSeat(seat);
            }
        }
        return schedule;
    }
}
