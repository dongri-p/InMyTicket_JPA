package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;

import lombok.Getter;

@Getter
public class SeatListDto {

    private Long seatId;
    private int seatNumber;
    private String grade;
    private int price;
    private SeatStatus status;

    public SeatListDto(Seat seat) {
        this.seatId = seat.getId();
        this.seatNumber = seat.getSeatNumber();
        this.grade = seat.getGrade();
        this.price = seat.getPrice();
        this.status = seat.getStatus();
    }
}
