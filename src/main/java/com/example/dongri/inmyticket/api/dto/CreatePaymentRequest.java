package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.PgProvider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreatePaymentRequest {

    @NotNull
    private Long reservationId;

    // TOSS: 토스페이먼츠 테스트 결제창, DEMO: 인증 없는 데모 결제
    @NotNull
    private PgProvider provider;

    @NotBlank
    @Size(max = 255)
    private String paymentKey;

    // 토스 주문번호 규칙: 영문·숫자·-·_ 6~64자
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_-]{6,64}$")
    private String orderId;

    @NotNull
    @Positive
    private Integer amount;
}
