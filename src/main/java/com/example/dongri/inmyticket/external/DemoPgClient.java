package com.example.dongri.inmyticket.external;

import org.springframework.stereotype.Component;

import com.example.dongri.inmyticket.domain.PgProvider;

import lombok.extern.slf4j.Slf4j;

// 인증 없이 바로 승인되는 데모 결제 (실제 청구 없음, PG 네트워크 지연만 재현).
// 토스 테스트 결제는 본인 카드 인증이 필요해서, 방문자가 부담 없이 예매 흐름을 체험할 수 있게 함께 둠.
// 동시성·커넥션 점유 테스트가 "PG 통신 중"인 1.5초 구간에 기대므로 지연 값은 유지
@Slf4j
@Component
public class DemoPgClient implements PgClient {

    private static final long SIMULATED_LATENCY_MS = 1500;

    @Override
    public PgProvider provider() {
        return PgProvider.DEMO;
    }

    @Override
    public void approve(String paymentKey, String orderId, int amount) {
        simulateNetworkCall("데모 결제 승인 완료.", "결제 통신 중 오류가 발생했습니다.");
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        simulateNetworkCall("데모 결제 취소(환불) 완료.", "환불 통신 중 오류가 발생했습니다.");
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
