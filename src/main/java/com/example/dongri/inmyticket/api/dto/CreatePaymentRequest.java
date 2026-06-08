package com.example.dongri.inmyticket.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePaymentRequest {

    private Long reservationId;
    private int amount;
    private String paymentKey;
}
