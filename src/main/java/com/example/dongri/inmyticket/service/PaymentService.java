package com.example.dongri.inmyticket.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // 클래스 위에 @Transactional이 없는게 핵심 최적화
public class PaymentService {
    
    private final PaymentApprovalService paymentApprovalService;

    // 외부 PG사 결제 승인 요청 껍데기 메서드
    public Long processPayment(Long reservationId, int amount, String paymentKey) {

        // 1. 외부 결제 대행사(PG) API 네트워크 통신 시뮬레이션
        try {
            Thread.sleep(1500);
            System.out.println("외부 PG사 결제 승인 통신 완료.");
        } catch(InterruptedException e) {
            throw new IllegalStateException("결제 통신 중 오류가 발생했습니다.", e);
        }

        // 2. 외부 통신이 성공하면, 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출
        return paymentApprovalService.approve(reservationId, amount, paymentKey);
    }
}
