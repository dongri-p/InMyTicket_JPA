package com.example.dongri.inmyticket.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreatePaymentRequest;
import com.example.dongri.inmyticket.service.PaymentService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentService paymentService;

    // 외부 결제 승인 완료 및 반영 API
    @PostMapping("/api/v1/payment")
    public CreatePaymentResponse pay(@RequestBody CreatePaymentRequest request) {

        // 외부-내부-외부 전략이 적용된 서비스 호출
        Long paymentId = paymentService.processPayment(
            request.getReservationId(),
            request.getAmount(),
            request.getPaymentKey()
        );

        return new CreatePaymentResponse(paymentId, "결제가 승인 완료되었습니다. 티켓 예매가 확정되었습니다.");
    }

    @Data
    @AllArgsConstructor
    static class CreatePaymentResponse {
        private Long paymentId;
        private String message;
    }
}
