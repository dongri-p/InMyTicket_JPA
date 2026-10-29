package com.example.dongri.inmyticket.domain;

// 취소 시점에 계산한 환불 내역. 결제 금액(paidAmount)에서 수수료(cancelFee)를 뺀 refundAmount만 PG로 돌려줌
public record RefundQuote(int paidAmount, int feeRate, int cancelFee, int refundAmount, String reason) {

    // 결제 전 예약 - 돌려줄 돈도 수수료도 없음
    public static RefundQuote nothingPaid() {
        return new RefundQuote(0, 0, 0, 0, "결제 전이라 환불할 금액이 없습니다.");
    }
}
