package com.example.dongri.inmyticket.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // PG 승인 취소(보상) 전, 해당 paymentKey가 이미 다른 결제로 기록돼 있는지 확인
    boolean existsByPaymentKey(String paymentKey);

    // 결제 재요청(멱등성) 확인용: paymentKey로 이미 기록된 결제와 그 예약·회원을 한 번에 조회
    @Query("select p from Payment p join fetch p.reservation r join fetch r.member where p.paymentKey = :paymentKey")
    Optional<Payment> findWithReservationByPaymentKey(@Param("paymentKey") String paymentKey);
}
