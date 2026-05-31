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
    private int availableSeatCount;

    // 연관관계 편의 메서드
    public void addSeat(Seat seat) {
        this.seats.add(seat);
        seat.setSchedule(this);
    }

    // 공연 회차 생성 시, 공연장 정보까지 포함하여 좌석을 자동 빌드하는 메서드
    public static Schedule creatSchedule(Performance performance, Hall hall, LocalDateTime starTime, int totalSeatCount) {
        Schedule schedule = new Schedule();
        schedule.setPerformance(performance);
        schedule.setHall(hall);
        schedule.setStartTime(starTime);
        schedule.setTotalSeatCount(totalSeatCount);
        schedule.setAvailableSeatCount(totalSeatCount);

        // 지정된 자석 수 만큼 가상 좌석 자동 생성
        for(int i = 1; i <= totalSeatCount; i++) {
            Seat seat = new Seat();
            seat.setSeatNumber(i);
            seat.setGrade("VIP");
            seat.setPrice(150000);
            seat.setStatus("AVAILABLE");

            schedule.addSeat(seat);
        }
        return schedule;
    }
}
