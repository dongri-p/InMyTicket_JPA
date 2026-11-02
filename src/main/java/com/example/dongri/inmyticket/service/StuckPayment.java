package com.example.dongri.inmyticket.service;

import com.example.dongri.inmyticket.domain.PgProvider;

// 결제 승인 결과를 모른 채 PROCESSING에 남은 예약. paymentKey가 없으면 이 정보를 저장하기 전에 시작한 결제
public record StuckPayment(Long reservationId, Long memberId, String paymentKey, PgProvider pgProvider) {
}
