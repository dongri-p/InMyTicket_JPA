package com.example.dongri.inmyticket.service;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

        try {
            // 1. 외부 결제 대행사(PG) API 네트워크 통신 시뮬레이션
            simulatePgCommunication("외부 PG사 결제 승인 통신 완료.", "결제 통신 중 오류가 발생했습니다.");

            // 2. 외부 통신이 성공하면, 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출
            return paymentApprovalService.approve(memberId, reservationId, paymentKey);
        } catch (RuntimeException e) {
            // PG 통신 실패든 approve() 내부 오류든, PROCESSING에 갇힌 채로 남으면
            // 취소도(PROCESSING 거부) 자동만료도(PENDING만 대상) 닿지 않는 회수 불가 상태가 되므로
            // 실패 시 항상 PENDING으로 되돌려 재시도/자동만료가 가능하게 함
            try {
                reservationService.revertProcessingToPending(reservationId);
            } catch (RuntimeException revertFailure) {
                // 되돌리기 자체가 실패하면(락 경합 등) 예약이 PROCESSING에 그대로 고착될 수 있음.
                // 원래 실패 원인(e)을 덮어쓰지 않고 그대로 재전파하되, 고착 가능성을 로그로 남겨
                // 운영 중 발견/수동 확인할 수 있게 함
                log.error("예약(id={})을 PENDING으로 되돌리는 데 실패했습니다. PROCESSING 상태로 고착되었을 수 있어 수동 확인이 필요합니다.",
                        reservationId, revertFailure);
            }
            throw e;
        }
    }

    // 예약 취소 시 결제가 완료된 상태라면 PG 환불 통신 이후 취소 반영
    public void processCancel(Long memberId, Long reservationId) {

        boolean refundNeeded = reservationService.hasCompletedPayment(memberId, reservationId);

        if (refundNeeded) {
            // 1. 외부 결제 대행사(PG) 환불 API 네트워크 통신 시뮬레이션
            simulatePgCommunication("외부 PG사 환불 통신 완료.", "환불 통신 중 오류가 발생했습니다.");
        }

        // 2. 외부 환불 통신이 성공하면(혹은 환불할 결제가 없으면), 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출.
        // 확인 시점 이후 결제가 새로 완료됐다면(refundNeeded=false였는데 실제로는 결제가 생김) 조용히 넘어가지 않고
        // cancelAfterRefundCheck가 재시도를 요구하는 예외를 던짐
        reservationService.cancelAfterRefundCheck(memberId, reservationId, refundNeeded);
    }

    // 외부 PG사 API 네트워크 통신 시뮬레이션 (승인/환불 공통)
    private void simulatePgCommunication(String successLogMessage, String failureMessage) {
        try {
            Thread.sleep(1500);
            log.info(successLogMessage);
        } catch (InterruptedException e) {
            throw new IllegalStateException(failureMessage, e);
        }
    }
}
