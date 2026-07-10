package com.example.dongri.inmyticket.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CancelReservationResponse;
import com.example.dongri.inmyticket.api.dto.CreateReservationRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.config.AuthenticatedMember;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservationApiController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;

    // 티켓 예매 API - memberId는 JWT에서 추출 (요청 바디로 받으면 타인 명의 예매 가능)
    @PostMapping("/api/v1/reservations")
    public CreateResourceResponse reserve(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Validated @RequestBody CreateReservationRequest request) {

        Long reservationId = reservationService.reserve(authenticatedMember.getMemberId(), request.getSeatId());

        return new CreateResourceResponse(reservationId, "티켓 예매가 완료되었습니다. 결제를 진행해주세요.");
    }

    // 예매 취소 API - 본인 예약만 취소 가능 (JWT로 신원 확인)
    @DeleteMapping("/api/v1/reservations/{reservationId}")
    public CancelReservationResponse cancel(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long reservationId) {

        paymentService.processCancel(authenticatedMember.getMemberId(), reservationId);

        return new CancelReservationResponse("예매가 취소되었습니다.");
    }

}
