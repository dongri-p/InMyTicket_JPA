package com.example.dongri.inmyticket.external;

import com.example.dongri.inmyticket.domain.PgProvider;

// 외부 결제 대행사(PG) 연동 창구. 토스페이먼츠(TossPgClient)와 데모 결제(DemoPgClient)가 있고,
// 결제 요청마다 고른 PG로 승인하고 취소할 때도 결제한 PG로 환불함.
// PaymentService에서 분리해 승인/승인취소 호출 여부를 테스트에서 검증할 수 있게 함
public interface PgClient {

    PgProvider provider();

    // 결제 승인 요청. 결제창에서 받은 paymentKey와 주문번호·금액을 PG에 보내 최종 승인
    void approve(String paymentKey, String orderId, int amount);

    // 승인된 결제 취소 요청 (사용자 예매 취소 시 환불, 승인 후 DB 반영 실패 시 보상 공통)
    void cancel(String paymentKey, String reason);
}
