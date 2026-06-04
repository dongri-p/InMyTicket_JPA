package com.example.dongri.inmyticket.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // 무분별한 new 생성 방지
public class Payment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id")
    private Reservation reservation;

    private int amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    // 외부 PG사가 발급해준 고유 결제 키
    private String paymentKey;

    private LocalDateTime paidAt;

    // 결제 객체 생성 메서드
    public static Payment createPayment(Reservation reservation, int amount, String paymentKey) {
        Payment payment = new Payment();
        payment.reservation = reservation;
        payment.amount = amount;
        payment.paymentKey = paymentKey;
        payment.status = PaymentStatus.COMPLETED; // 외부 API 승인이 성공했다고 가정하고 완료 상태로 생성
        payment.paidAt = LocalDateTime.now();
        return payment;
    }
    
}

