package com.example.dongri.inmyticket.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreatePaymentResponse {

    private Long paymentId;
    private String message;

}
