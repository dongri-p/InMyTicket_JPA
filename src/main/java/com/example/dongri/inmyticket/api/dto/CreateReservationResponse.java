package com.example.dongri.inmyticket.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateReservationResponse {

    private Long reservationId;
    private String message;

}
