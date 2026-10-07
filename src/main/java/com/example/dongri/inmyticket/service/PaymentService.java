package com.example.dongri.inmyticket.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.dongri.inmyticket.external.PgClient;
import com.example.dongri.inmyticket.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor // 클래스 위에 @Transactional이 없는게 핵심 최적화
public class PaymentService {

    private final PaymentApprovalService paymentApprovalService;
    private final ReservationService reservationService;
    private final PgClient pgClient;
    private final PaymentRepository paymentRepository;

    // 외부 PG사 결제 승인 요청 껍데기 메서드
    public Long processPayment(Long memberId, Long reservationId, String paymentKey) {

        // 0. 긴 PG 통신에 들어가기 전, 예약을 PROCESSING으로 전환해 자동만료 스케줄러가
        // 결제 진행 중인 예약을 건드리지 못하게 함
        reservationService.beginPaymentProcessing(memberId, reservationId);

        // 1. 외부 결제 대행사(PG) 승인 요청 - 실패하면 승인된 돈이 없으므로 PENDING으로만 되돌림
        try {
            pgClient.approve(paymentKey);
        } catch (RuntimeException e) {
            revertToPendingOrLog(reservationId);
            throw e;
        }

        // 2. 외부 승인이 성공하면, 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출
        try {
            return paymentApprovalService.approve(memberId, reservationId, paymentKey);
        } catch (RuntimeException e) {
            // PG에서는 승인(돈이 빠져나감)됐는데 DB 반영이 실패하면 결제 기록 없이 청구만 남음.
            // 분산 트랜잭션으로 묶을 수 없으므로 PG 승인 취소로 되돌리는 보상 처리를 먼저 수행
            compensatePgApproval(reservationId, paymentKey);
            revertToPendingOrLog(reservationId);
            throw e;
        }
    }

    // 승인 후 DB 반영에 실패한 PG 결제를 취소하는 보상 처리
    private void compensatePgApproval(Long reservationId, String paymentKey) {
        // 이미 다른 결제로 기록된 paymentKey라면(중복 키 재사용 등으로 approve()가 unique 제약에 걸린 경우)
        // 그 키를 취소하면 정상 완료된 남의 결제를 환불해버리게 되므로 보상하지 않음.
        // (실제 PG는 이미 처리된 키의 승인 요청을 PG 단계에서 거절하므로 여기까지 오지 않음)
        if (paymentRepository.existsByPaymentKey(paymentKey)) {
            log.warn("예약(id={}) 결제 실패 - paymentKey가 이미 다른 결제로 기록되어 있어 PG 승인 취소를 생략합니다.", reservationId);
            return;
        }
        try {
            pgClient.cancel(paymentKey);
            log.warn("예약(id={}) 결제 DB 반영 실패로 PG 승인을 취소(보상)했습니다.", reservationId);
        } catch (RuntimeException cancelFailure) {
            // 보상까지 실패하면 고객에게 청구만 남은 상태 - 원래 실패 원인을 가리지 않도록 로그만 남기고 수동 환불 대상으로 표시
            log.error("예약(id={}) PG 승인 취소(보상)에 실패했습니다. paymentKey={} 수동 환불이 필요합니다.",
                    reservationId, paymentKey, cancelFailure);
        }
    }

    // PG 통신 실패든 approve() 내부 오류든, PROCESSING에 갇힌 채로 남으면
    // 취소도(PROCESSING 거부) 자동만료도(PENDING만 대상) 닿지 않는 회수 불가 상태가 되므로
    // 실패 시 항상 PENDING으로 되돌려 재시도/자동만료가 가능하게 함
    private void revertToPendingOrLog(Long reservationId) {
        try {
            reservationService.revertProcessingToPending(reservationId);
        } catch (RuntimeException revertFailure) {
            // 되돌리기 자체가 실패하면(락 경합 등) 예약이 PROCESSING에 그대로 고착될 수 있음.
            // 원래 실패 원인을 덮어쓰지 않도록 여기서 삼키고, 고착 가능성을 로그로 남김
            // (고착된 예약은 ReservationExpirationScheduler가 일정 시간 후 PENDING으로 복구)
            log.error("예약(id={})을 PENDING으로 되돌리는 데 실패했습니다. PROCESSING 상태로 고착되었을 수 있어 수동 확인이 필요합니다.",
                    reservationId, revertFailure);
        }
    }

    // 예약 취소 시 결제가 완료된 상태라면 PG 환불 통신 이후 취소 반영
    public void processCancel(Long memberId, Long reservationId) {

        Optional<String> paymentKeyToRefund = reservationService.findPaymentKeyToRefund(memberId, reservationId);

        // 1. 외부 결제 대행사(PG) 환불 요청
        paymentKeyToRefund.ifPresent(pgClient::cancel);

        // 2. 외부 환불 통신이 성공하면(혹은 환불할 결제가 없으면), 진짜 DB를 업데이트하는 '짧은 트랜잭션 서비스'를 호출.
        // 확인 시점 이후 결제가 새로 완료됐다면(환불 대상이 없었는데 실제로는 결제가 생김) 조용히 넘어가지 않고
        // cancelAfterRefundCheck가 재시도를 요구하는 예외를 던짐
        reservationService.cancelAfterRefundCheck(memberId, reservationId, paymentKeyToRefund.isPresent());
    }
}
