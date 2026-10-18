package com.example.dongri.inmyticket.domain;

// 결제를 처리하는 PG 종류.
// TOSS: 토스페이먼츠 테스트 결제창(본인 카드 인증 필요, 청구 없음)
// DEMO: 인증 없이 바로 승인되는 데모 결제(방문자가 부담 없이 예매 흐름을 체험하도록)
public enum PgProvider {
    TOSS,
    DEMO
}
