package com.example.dongri.inmyticket.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

// approve() 실패 후 되돌리기(revertProcessingToPending)마저 실패하는, 실제 DB로 재현하기 어려운
// 락 경합 시나리오를 검증하기 위해 협력 객체를 목으로 대체하는 순수 단위 테스트(14차 발견)
public class PaymentServiceRevertFailureTest {

    @Test
    @DisplayName("approve() 실패 후 되돌리기마저 실패해도, 되돌리기 실패가 아닌 원래 실패 원인이 그대로 전파된다")
    void processPayment_whenRevertAlsoFails_originalExceptionIsPropagatedNotSwallowed() {
        // given
        ReservationService reservationService = Mockito.mock(ReservationService.class);
        PaymentApprovalService paymentApprovalService = Mockito.mock(PaymentApprovalService.class);

        IllegalStateException originalFailure = new IllegalStateException("결제할 수 없는 예약 상태입니다.");
        Mockito.doThrow(originalFailure)
                .when(paymentApprovalService).approve(1L, 100L, "key");
        Mockito.doThrow(new RuntimeException("락 경합으로 되돌리기 실패"))
                .when(reservationService).revertProcessingToPending(100L);

        PaymentService paymentService = new PaymentService(paymentApprovalService, reservationService);

        // when
        RuntimeException thrown = Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(1L, 100L, "key"));

        // then: 되돌리기 실패로 원래 예외가 가려지지 않고 그대로(동일 인스턴스) 전파되어야 함
        Assertions.assertSame(originalFailure, thrown);
    }
}
