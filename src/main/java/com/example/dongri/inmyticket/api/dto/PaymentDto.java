package com.example.dongri.inmyticket.api.dto;

import java.time.LocalDateTime;

import com.example.dongri.inmyticket.domain.Payment;

import lombok.Getter;

@Getter
public class PaymentDto {

    private Long paymentId;
    private Long reservationId;
    private int amount;
    private String status;
    private String provider;
    private LocalDateTime paidAt;

    public PaymentDto(Payment payment) {
        this.paymentId = payment.getId();
        this.reservationId = payment.getReservation().getId();
        this.amount = payment.getAmount();
        this.status = payment.getStatus().name();
        this.provider = payment.getPgProvider().name();
        this.paidAt = payment.getPaidAt();
    }
}
