package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePaymentRequest {

    @NotNull
    private Long reservationId;

    @NotBlank
    private String paymentKey;
}
