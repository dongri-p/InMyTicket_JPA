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
    public Long approve(Long reservationId, int amount, String paymentKey) {

        // 1. 예약 조회
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 예약입니다. id=" + reservationId));
        
        // 2. 결제 엔티티 생성(내부 상태 COMPLETED로 세팅)
        Payment payment = Payment.createPayment(reservation, amount, paymentKey);
        paymentRepository.save(payment);

        // 3. 예약의 상태도 결제 완료로 변경
        reservation.setStatus(ReservationStatus.CONFIRMED);

        return payment.getId();
    }
    
}
