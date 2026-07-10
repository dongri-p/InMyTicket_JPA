package com.example.dongri.inmyticket.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor // 클래스 위에 @Transactional이 없는게 핵심 최적화
public class PaymentService {

    private final PaymentApprovalService paymentApprovalService;
    private final ReservationService reservationService;

    // 외부 PG사 결제 승인 요청 껍데기 메서드
    public Long processPayment(Long memberId, Long reservationId, String paymentKey) {

        // 0. 긴 PG 통신에 들어가기 전, 예약을 PROCESSING으로 전환해 자동만료 스케줄러가
        // 결제 진행 중인 예약을 건드리지 못하게 함
        reservationService.beginPaymentProcessing(memberId, reservationId);

        // 1. 외부 결제 대행사(PG) API 네트워크 통신 시뮬레이션
        try {
            Thread.sleep(1500);
            System.out.println("외부 PG사 결제 승인 통신 완료.");
        } catch(InterruptedException e) {
            // PG 통신이 실패했으므로 PENDING으로 되돌려 재시도/자동만료가 가능하게 함
            reservationService.revertProcessingToPending(reservationId);
            throw new IllegalStateException("결제 통신 중 오류가 발생했습니다.", e);
        }

        // 2. 외부 통신이 성공하면, 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출
        return paymentApprovalService.approve(memberId, reservationId, paymentKey);
    }

    // 예약 취소 시 결제가 완료된 상태라면 PG 환불 통신 이후 취소 반영
    public void processCancel(Long memberId, Long reservationId) {

        boolean refundNeeded = reservationService.hasCompletedPayment(memberId, reservationId);

        if (refundNeeded) {
            // 1. 외부 결제 대행사(PG) 환불 API 네트워크 통신 시뮬레이션
            try {
                Thread.sleep(1500);
                System.out.println("외부 PG사 환불 통신 완료.");
            } catch (InterruptedException e) {
                throw new IllegalStateException("환불 통신 중 오류가 발생했습니다.", e);
            }
        }

        // 2. 외부 환불 통신이 성공하면(혹은 환불할 결제가 없으면), 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출.
        // 확인 시점 이후 결제가 새로 완료됐다면(refundNeeded=false였는데 실제로는 결제가 생김) 조용히 넘어가지 않고
        // cancelAfterRefundCheck가 재시도를 요구하는 예외를 던짐
        reservationService.cancelAfterRefundCheck(memberId, reservationId, refundNeeded);
    }
}
