package com.example.dongri.inmyticket;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.dongri.inmyticket.domain.CancelFeePolicy;
import com.example.dongri.inmyticket.domain.RefundQuote;

// 취소 수수료 구간 경계 - 공연 11.20 19:00, 결제 270,000원 기준
public class CancelFeePolicyTest {

    private static final LocalDateTime SHOW = LocalDateTime.of(2026, 11, 20, 19, 0);
    private static final LocalDateTime LONG_AGO = LocalDateTime.of(2026, 10, 1, 12, 0);
    private static final int PAID = 270000;

    @Test
    @DisplayName("공연 7일 전 날(11.13)까지는 무료, 6일 전(11.14) 0시부터 10%")
    void sevenDaysBoundary() {
        assertFee(0, quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 13, 23, 59)));
        assertFee(10, quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 14, 0, 0)));
    }

    @Test
    @DisplayName("3일 전(11.17)까지 10%, 2일 전(11.18) 0시부터 시작 직전까지 30%")
    void twoDaysBoundary() {
        assertFee(10, quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 17, 23, 59)));
        assertFee(30, quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 18, 0, 0)));
        assertFee(30, quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 20, 18, 59)));
    }

    @Test
    @DisplayName("예매 후 24시간 안이면 10% 구간이어도 무료, 24시간이 지나면 시점 기준")
    void within24Hours_free() {
        LocalDateTime reservedAt = LocalDateTime.of(2026, 11, 15, 10, 0);

        RefundQuote within = quoteAt(reservedAt, LocalDateTime.of(2026, 11, 16, 9, 59));
        assertFee(0, within);
        Assertions.assertEquals("예매 후 24시간 이내 취소", within.reason());
        assertFee(10, quoteAt(reservedAt, LocalDateTime.of(2026, 11, 16, 10, 0)));
    }

    @Test
    @DisplayName("공연 2일 전부터 한 예매는 24시간 안이어도 30% (직전에 사서 무료로 무르는 것 방지)")
    void within24Hours_butTwoDaysBefore_charged() {
        LocalDateTime reservedAt = LocalDateTime.of(2026, 11, 18, 10, 0);

        assertFee(30, quoteAt(reservedAt, LocalDateTime.of(2026, 11, 18, 11, 0)));
    }

    @Test
    @DisplayName("수수료를 뺀 금액만 돌려준다")
    void refundIsPaidMinusFee() {
        RefundQuote quote = quoteAt(LONG_AGO, LocalDateTime.of(2026, 11, 15, 12, 0));

        Assertions.assertEquals(PAID, quote.paidAmount());
        Assertions.assertEquals(27000, quote.cancelFee());
        Assertions.assertEquals(243000, quote.refundAmount());
    }

    private RefundQuote quoteAt(LocalDateTime reservedAt, LocalDateTime now) {
        return CancelFeePolicy.quote(PAID, reservedAt, SHOW, now);
    }

    private void assertFee(int expectedRate, RefundQuote quote) {
        Assertions.assertEquals(expectedRate, quote.feeRate(), quote.reason());
        Assertions.assertEquals(PAID * expectedRate / 100, quote.cancelFee());
        Assertions.assertEquals(PAID - quote.cancelFee(), quote.refundAmount());
    }
}
