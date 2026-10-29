package com.example.dongri.inmyticket.domain;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 예매 취소 수수료 (예매처 방식)
// - 예매 후 24시간 안: 무료. 단 공연 2일 전부터는 아래 시점 기준을 따름 (공연 직전에 사서 무료로 무르는 걸 막음)
// - 공연 7일 전까지: 무료 / 6~3일 전: 10% / 2일 전 ~ 시작 전: 30% / 시작 후: 취소 불가 (Reservation.isShowStarted)
// 며칠 전인지는 시각이 아니라 달력 날짜로 셈 (공연 11.20 19:00이면 11.13까지 무료, 11.14~11.17 10%, 11.18부터 30%)
public final class CancelFeePolicy {

    private CancelFeePolicy() {
    }

    public static RefundQuote quote(int paidAmount, LocalDateTime reservedAt, LocalDateTime showStart, LocalDateTime now) {
        long daysBefore = ChronoUnit.DAYS.between(now.toLocalDate(), showStart.toLocalDate());

        if (daysBefore >= 3 && now.isBefore(reservedAt.plusHours(24))) {
            return of(paidAmount, 0, "예매 후 24시간 이내 취소");
        }
        if (daysBefore >= 7) {
            return of(paidAmount, 0, "공연 7일 전까지 취소");
        }
        if (daysBefore >= 3) {
            return of(paidAmount, 10, "공연 6~3일 전 취소");
        }
        return of(paidAmount, 30, "공연 2일 전부터 취소");
    }

    // 수수료는 원 단위 버림 (좌석 가격이 천 원 단위라 실제로는 나누어떨어짐)
    private static RefundQuote of(int paidAmount, int feeRate, String reason) {
        int cancelFee = paidAmount * feeRate / 100;
        return new RefundQuote(paidAmount, feeRate, cancelFee, paidAmount - cancelFee, reason);
    }
}
