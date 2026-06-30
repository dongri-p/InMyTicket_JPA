package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateReservationRequest {

    @NotNull
    private Long seatId;
}
