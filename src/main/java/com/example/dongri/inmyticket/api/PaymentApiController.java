package com.example.dongri.inmyticket.api;

import com.example.dongri.inmyticket.api.dto.PaymentDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreatePaymentRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.config.AuthenticatedMember;
import com.example.dongri.inmyticket.service.PaymentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;

    // 외부 결제 승인 완료 및 반영 API
    @PostMapping("/api/v1/payments")
    public ResponseEntity<CreateResourceResponse> pay(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Validated @RequestBody CreatePaymentRequest request) {

        // 외부-내부-외부 전략이 적용된 서비스 호출
        Long paymentId = paymentService.processPayment(
            authenticatedMember.getMemberId(),
            request.getReservationId(),
            request.getProvider(),
            request.getPaymentKey(),
            request.getOrderId(),
            request.getAmount()
        );

        return CreateResourceResponse.created(paymentId);
    }

    // 결제 단건 조회 API - 본인 예약의 결제만 조회 가능
    @GetMapping("/api/v1/payments/{paymentId}")
    public PaymentDto payment(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long paymentId) {

        return paymentService.findMyPayment(authenticatedMember.getMemberId(), paymentId);
    }
}
