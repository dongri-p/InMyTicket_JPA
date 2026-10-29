package com.example.dongri.inmyticket.service;

import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.RefundQuote;

// 환불 요청에 필요한 것: 결제한 PG·paymentKey와 돌려줄 금액.
// PG 환불(트랜잭션 밖)과 DB 반영(짧은 트랜잭션)이 같은 금액을 쓰도록, 한 번 계산한 값을 그대로 넘김
public record RefundPlan(String paymentKey, PgProvider pgProvider, RefundQuote quote) {
}
