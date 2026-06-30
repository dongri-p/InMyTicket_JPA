package com.example.dongri.inmyticket.api;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateReservationRequest;
import com.example.dongri.inmyticket.config.AuthenticatedMember;
import com.example.dongri.inmyticket.service.ReservationService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservationApiController {

    private final ReservationService reservationService;

    // 티켓 예매 API - memberId는 JWT에서 추출 (요청 바디로 받으면 타인 명의 예매 가능)
    @PostMapping("/api/v1/reservations")
    public CreateReservationResponse reserve(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Validated @RequestBody CreateReservationRequest request) {

        Long reservationId = reservationService.reserve(authenticatedMember.getMemberId(), request.getSeatId());

        return new CreateReservationResponse(reservationId, "티켓 예매가 완료되었습니다. 결제를 진행해주세요.");
    }

    @Data
    @AllArgsConstructor
    static class CreateReservationResponse {
        private Long reservationId;
        private String message;
    }
    
}
