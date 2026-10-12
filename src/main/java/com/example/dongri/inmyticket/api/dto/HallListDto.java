package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.Hall;

import lombok.Getter;

@Getter
public class HallListDto {

    private Long hallId;
    private String name;
    private String address;
    private int totalSeats;

    public HallListDto(Hall hall) {
        this.hallId = hall.getId();
        this.name = hall.getName();
        this.address = hall.getAddress();
        this.totalSeats = hall.getTotalSeats();
    }
}
