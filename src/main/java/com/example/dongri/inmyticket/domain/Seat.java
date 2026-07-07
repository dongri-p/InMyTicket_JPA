package com.example.dongri.inmyticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Seat {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private Schedule schedule;

    private String grade;
    private int seatNumber;
    private int price;

    @Enumerated(EnumType.STRING)
    private SeatStatus status;

    @Version
    private Long version;


    // 좌석 예매 처리
    public void reserve() {

        if (this.status != SeatStatus.AVAILABLE) {
            throw new IllegalStateException("이미 예매 완료된 좌석입니다.");
        }

        this.status = SeatStatus.RESERVED;
    }

    // 좌석 예매 취소 시 다시 예매 가능 상태로 되돌림
    public void release() {

        if (this.status != SeatStatus.RESERVED) {
            throw new IllegalStateException("예매되지 않은 좌석은 취소할 수 없습니다.");
        }

        this.status = SeatStatus.AVAILABLE;
    }
}
