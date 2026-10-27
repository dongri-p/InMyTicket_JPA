package com.example.dongri.inmyticket.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateReservationRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.api.dto.ListResult;
import com.example.dongri.inmyticket.api.dto.ReservationListDto;
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
    public ResponseEntity<CreateResourceResponse> reserve(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @Validated @RequestBody CreateReservationRequest request) {

        Long reservationId = reservationService.reserve(authenticatedMember.getMemberId(), request.getSeatIds());

        return CreateResourceResponse.created(reservationId);
    }

    // 내 예매 목록 조회 API
    @GetMapping("/api/v1/reservations/me")
    public ListResult<List<ReservationListDto>> myReservations(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {

        List<ReservationListDto> reservations =
                reservationService.findMyReservations(authenticatedMember.getMemberId());

        return new ListResult<>(reservations.size(), reservations.size(), reservations);
    }

    // 예매 취소 API - 본인 예약만 취소 가능 (JWT로 신원 확인)
    // 예약 행을 지우지 않고 CANCELLED로 바꾸며 환불까지 하는 상태 전이라 DELETE 대신 취소 동작 URL로 둠
    @PostMapping("/api/v1/reservations/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
            @PathVariable Long reservationId) {

        paymentService.processCancel(authenticatedMember.getMemberId(), reservationId);
    }

}
