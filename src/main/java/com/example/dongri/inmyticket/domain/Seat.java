package com.example.dongri.inmyticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    private String status;

    @Version
    private Long version;


    // 좌석 예매 처리
    public void reserve() {

        // 이미 누군가 예매를 한 상태라면 예외 터트리기
        if("RESERVED".equals(this.status)) {
            throw new IllegalStateException("이미 예매 완료된 좌석입니다.");
        }

        // 문제가 없다면 상태를 RESERVED로 변경
        this.setStatus("RESERVED");

        if(this.schedule != null) {
            int currentAvailable = this.schedule.getAvailableSeatCount();
            if(currentAvailable > 0) {
                this.schedule.setAvailableSeatCount(currentAvailable - 1);
            }
        }
    }
}
