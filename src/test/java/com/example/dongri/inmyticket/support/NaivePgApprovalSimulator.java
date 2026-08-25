package com.example.dongri.inmyticket.support;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

/**
 * 테스트 전용: "리팩터링 전" 설계를 재현하기 위한 시뮬레이터.
 * PaymentApprovalService.approve()와 동일한 DB 작업을 수행하되,
 * PG 통신(1.5초)을 트랜잭션 "안쪽"에서 수행한다 — 이 프로젝트가 실제로 겪었던
 * 문제(외부 API 대기 동안 DB 커넥션을 붙잡고 있는 구조)를 재현해 개선 전/후를
 * 같은 인프라(H2 TCP)로 비교 측정하기 위한 용도.
 */
@Component
@RequiredArgsConstructor
public class NaivePgApprovalSimulator {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public Long approveWithInlinePgCall(Long memberId, Long reservationId, String paymentKey) {
        // 1. 예약 조회 (이 시점에 커넥션이 체크아웃됨)
        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        reservation.assertOwner(memberId);

        if (reservation.getStatus() == ReservationStatus.CONFIRMED || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("결제할 수 없는 예약 상태입니다. status=" + reservation.getStatus());
        }

        // 2. [개선 전 설계] 외부 PG 통신을 트랜잭션 "안에서" 그대로 수행 — 이 동안 DB 커넥션 점유
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            throw new IllegalStateException("결제 통신 중 오류가 발생했습니다.", e);
        }

        // 3. PG 통신 이후 DB 반영
        Payment payment = Payment.createPayment(reservation, reservation.getTotalPrice(), paymentKey);
        paymentRepository.save(payment);
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return payment.getId();
    }
}
