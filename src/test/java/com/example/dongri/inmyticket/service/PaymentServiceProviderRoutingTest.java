package com.example.dongri.inmyticket.service;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.example.dongri.inmyticket.domain.RefundQuote;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.external.PgClient;
import com.example.dongri.inmyticket.repository.PaymentRepository;

// 토스·데모 결제를 함께 두므로, 고른 PG로 승인하고 취소할 때는 결제했던 PG로 환불하는지 검증
public class PaymentServiceProviderRoutingTest {

    private ReservationService reservationService;
    private PgClient tossPgClient;
    private PgClient demoPgClient;
    private PaymentService paymentService;

    private static final RefundQuote TEN_PERCENT_FEE = new RefundQuote(150000, 10, 15000, 135000, "공연 6~3일 전 취소");

    @BeforeEach
    void setUp() {
        reservationService = Mockito.mock(ReservationService.class);
        tossPgClient = Mockito.mock(PgClient.class);
        Mockito.when(tossPgClient.provider()).thenReturn(PgProvider.TOSS);
        demoPgClient = Mockito.mock(PgClient.class);
        Mockito.when(demoPgClient.provider()).thenReturn(PgProvider.DEMO);
        paymentService = new PaymentService(Mockito.mock(PaymentApprovalService.class), reservationService,
                List.of(tossPgClient, demoPgClient), Mockito.mock(PaymentRepository.class));
    }

    @Test
    @DisplayName("토스로 결제하면 토스에만 승인 요청을 보낸다")
    void tossPayment_approvedByToss() {
        paymentService.processPayment(1L, 100L, PgProvider.TOSS, "toss-key", "imt-100-abc123", 150000);

        Mockito.verify(tossPgClient).approve("toss-key", "imt-100-abc123", 150000);
        Mockito.verify(demoPgClient, Mockito.never()).approve(Mockito.any(), Mockito.any(), Mockito.anyInt());
    }

    @Test
    @DisplayName("토스로 결제한 예약을 취소하면 토스로 환불한다")
    void cancelTossPayment_refundedByToss() {
        Mockito.when(reservationService.prepareRefund(1L, 100L))
                .thenReturn(Optional.of(new RefundPlan("toss-key", PgProvider.TOSS, TEN_PERCENT_FEE)));

        paymentService.processCancel(1L, 100L);

        // 수수료를 뺀 금액만 환불 요청하고, DB에도 같은 환불 내역을 넘김
        Mockito.verify(tossPgClient).cancel(Mockito.eq("toss-key"), Mockito.anyString(), Mockito.eq(135000));
        Mockito.verify(demoPgClient, Mockito.never()).cancel(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(reservationService).cancelAfterRefundCheck(1L, 100L, TEN_PERCENT_FEE);
    }

    @Test
    @DisplayName("데모로 결제한 예약을 취소하면 토스를 부르지 않고 데모로 환불한다")
    void cancelDemoPayment_refundedByDemo() {
        Mockito.when(reservationService.prepareRefund(1L, 100L))
                .thenReturn(Optional.of(new RefundPlan("demo-key", PgProvider.DEMO, TEN_PERCENT_FEE)));

        paymentService.processCancel(1L, 100L);

        Mockito.verify(demoPgClient).cancel(Mockito.eq("demo-key"), Mockito.anyString(), Mockito.eq(135000));
        Mockito.verify(tossPgClient, Mockito.never()).cancel(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("돌려줄 금액이 0원이면(0원 좌석) PG를 부르지 않고 취소만 반영한다")
    void cancelZeroAmount_skipsPg() {
        RefundQuote free = new RefundQuote(0, 0, 0, 0, "공연 7일 전까지 취소");
        Mockito.when(reservationService.prepareRefund(1L, 100L))
                .thenReturn(Optional.of(new RefundPlan("demo-key", PgProvider.DEMO, free)));

        paymentService.processCancel(1L, 100L);

        Mockito.verify(demoPgClient, Mockito.never()).cancel(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(reservationService).cancelAfterRefundCheck(1L, 100L, free);
    }
}
