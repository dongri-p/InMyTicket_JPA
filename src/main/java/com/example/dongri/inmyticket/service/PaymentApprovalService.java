package com.example.dongri.inmyticket.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentApprovalService {


    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;

    // DB 작업만 수행하는 핵심 트랜잭션 로직
    public Long approve(Long memberId, Long reservationId, String paymentKey) {

        // 1. 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        // 2. 예약 소유자 검증 (타인 명의 결제 방지)
        if (!reservation.getMember().getId().equals(memberId)) {
            throw new AccessDeniedException("본인의 예약만 결제할 수 있습니다.");
        }

        // 3. 결제 가능한 상태(PENDING)인지 확인 (중복 결제, 취소된 예약 결제 방지)
        if (reservation.getStatus() != ReservationStatus.PENDING) {
            throw new IllegalStateException("결제할 수 없는 예약 상태입니다. status=" + reservation.getStatus());
        }

        // 4. 결제 금액은 서버의 예약 금액 사용 (클라이언트 조작 방지)
        Payment payment = Payment.createPayment(reservation, reservation.getTotalPrice(), paymentKey);
        paymentRepository.save(payment);

        // 5. 예약의 상태도 결제 완료로 변경
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return payment.getId();
    }
    
}
