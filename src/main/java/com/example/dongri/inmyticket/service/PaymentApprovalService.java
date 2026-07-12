package com.example.dongri.inmyticket.service;

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

        // 1. 예약 조회 (취소와 동시 발생 시 경쟁을 막기 위해 비관적 락 사용)
        Reservation reservation = reservationRepository.findByIdWithLock(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));

        // 2. 예약 소유자 검증 (타인 명의 결제 방지)
        reservation.assertOwner(memberId);

        // 3. 결제 가능한 상태인지 확인 (PENDING: 단위테스트 등에서 직접 승인, PROCESSING: PaymentService 정상 흐름)
        // CONFIRMED(중복 결제)/CANCELLED(취소된 예약 결제)만 명시적으로 거부
        if (reservation.getStatus() == ReservationStatus.CONFIRMED || reservation.getStatus() == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("결제할 수 없는 예약 상태입니다. status=" + reservation.getStatus());
        }

        // beginPaymentProcessing()에서도 조기 검사하지만, approve()는 단위테스트 등에서
        // 직접 호출되기도 하므로 여기서도 최종 방어선으로 다시 확인
        if (reservation.isShowStarted()) {
            throw new IllegalStateException("공연이 이미 시작되어 결제를 진행할 수 없습니다.");
        }

        // 4. 결제 금액은 서버의 예약 금액 사용 (클라이언트 조작 방지)
        Payment payment = Payment.createPayment(reservation, reservation.getTotalPrice(), paymentKey);
        paymentRepository.save(payment);

        // 5. 예약의 상태도 결제 완료로 변경
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return payment.getId();
    }
    
}
