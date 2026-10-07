package com.example.dongri.inmyticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // PG 승인 취소(보상) 전, 해당 paymentKey가 이미 다른 결제로 기록돼 있는지 확인
    boolean existsByPaymentKey(String paymentKey);
}
