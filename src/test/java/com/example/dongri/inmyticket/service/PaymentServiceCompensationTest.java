package com.example.dongri.inmyticket.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.example.dongri.inmyticket.external.PgClient;
import com.example.dongri.inmyticket.repository.PaymentRepository;

// PG 승인은 성공했는데 DB 반영(approve)이 실패하는 경우의 보상(PG 승인 취소) 처리를 검증하는 단위 테스트.
// "PG 성공 + DB 실패"는 실제 DB로 재현하기 어려워 협력 객체를 목으로 대체
public class PaymentServiceCompensationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long RESERVATION_ID = 100L;
    private static final String PAYMENT_KEY = "key";

    private ReservationService reservationService;
    private PaymentApprovalService paymentApprovalService;
    private PgClient pgClient;
    private PaymentRepository paymentRepository;
    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        reservationService = Mockito.mock(ReservationService.class);
        paymentApprovalService = Mockito.mock(PaymentApprovalService.class);
        pgClient = Mockito.mock(PgClient.class);
        paymentRepository = Mockito.mock(PaymentRepository.class);
        paymentService = new PaymentService(paymentApprovalService, reservationService, pgClient, paymentRepository);
    }

    @Test
    @DisplayName("PG 승인 후 DB 반영이 실패하면 PG 승인을 취소(보상)하고 예약을 PENDING으로 되돌린다")
    void approveFailsAfterPgSuccess_cancelsPgApprovalThenRevertsToPending() {
        // given
        IllegalStateException dbFailure = new IllegalStateException("DB 반영 실패");
        Mockito.doThrow(dbFailure).when(paymentApprovalService).approve(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY);

        // when
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY));

        // then: 원래 실패 원인이 전파되고, 보상 → PENDING 되돌리기 순서로 수행됨
        Assertions.assertSame(dbFailure, thrown);
        InOrder inOrder = Mockito.inOrder(pgClient, reservationService);
        inOrder.verify(pgClient).approve(PAYMENT_KEY);
        inOrder.verify(pgClient).cancel(PAYMENT_KEY);
        inOrder.verify(reservationService).revertProcessingToPending(RESERVATION_ID);
    }

    @Test
    @DisplayName("PG 승인 자체가 실패하면 취소할 승인이 없으므로 보상 요청을 보내지 않는다")
    void pgApprovalFails_doesNotSendCancel() {
        // given
        Mockito.doThrow(new IllegalStateException("결제 통신 중 오류가 발생했습니다."))
                .when(pgClient).approve(PAYMENT_KEY);

        // when
        Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY));

        // then
        Mockito.verify(pgClient, Mockito.never()).cancel(Mockito.anyString());
        Mockito.verify(paymentApprovalService, Mockito.never()).approve(Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(reservationService).revertProcessingToPending(RESERVATION_ID);
    }

    @Test
    @DisplayName("보상(PG 승인 취소)마저 실패해도 원래 실패 원인이 전파되고 예약은 PENDING으로 되돌린다")
    void compensationFails_originalExceptionPropagated_andStillReverts() {
        // given
        IllegalStateException dbFailure = new IllegalStateException("DB 반영 실패");
        Mockito.doThrow(dbFailure).when(paymentApprovalService).approve(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY);
        Mockito.doThrow(new IllegalStateException("환불 통신 중 오류가 발생했습니다."))
                .when(pgClient).cancel(PAYMENT_KEY);

        // when
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY));

        // then
        Assertions.assertSame(dbFailure, thrown);
        Mockito.verify(reservationService).revertProcessingToPending(RESERVATION_ID);
    }

    @Test
    @DisplayName("이미 다른 결제로 기록된 paymentKey면 남의 결제를 환불하지 않도록 보상을 생략한다")
    void paymentKeyAlreadyRecorded_skipsCompensation() {
        // given: 이미 완료된 결제의 paymentKey를 재사용해 approve()가 unique 제약에 걸린 상황
        Mockito.doThrow(new IllegalStateException("중복 paymentKey"))
                .when(paymentApprovalService).approve(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY);
        Mockito.when(paymentRepository.existsByPaymentKey(PAYMENT_KEY)).thenReturn(true);

        // when
        Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(MEMBER_ID, RESERVATION_ID, PAYMENT_KEY));

        // then
        Mockito.verify(pgClient, Mockito.never()).cancel(Mockito.anyString());
        Mockito.verify(reservationService).revertProcessingToPending(RESERVATION_ID);
    }
}
