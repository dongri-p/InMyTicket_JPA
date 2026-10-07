package com.example.dongri.inmyticket.external;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// 외부 결제 대행사(PG) API 연동 시뮬레이션 (실제 청구 없음, 네트워크 지연만 재현).
// PaymentService에서 분리해 승인/승인취소 호출 여부를 테스트에서 검증할 수 있게 함
@Slf4j
@Component
public class PgClient {

    private static final long SIMULATED_LATENCY_MS = 1500;

    // 결제 승인 요청
    public void approve(String paymentKey) {
        simulateNetworkCall("외부 PG사 결제 승인 통신 완료.", "결제 통신 중 오류가 발생했습니다.");
    }

    // 승인된 결제 취소 요청 (사용자 예매 취소 시 환불, 승인 후 DB 반영 실패 시 보상 공통)
    public void cancel(String paymentKey) {
        simulateNetworkCall("외부 PG사 결제 취소(환불) 통신 완료.", "환불 통신 중 오류가 발생했습니다.");
    }

    private void simulateNetworkCall(String successLogMessage, String failureMessage) {
        try {
            Thread.sleep(SIMULATED_LATENCY_MS);
            log.info(successLogMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage, e);
        }
    }
}
