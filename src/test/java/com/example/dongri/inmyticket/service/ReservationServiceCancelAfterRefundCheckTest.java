package com.example.dongri.inmyticket.service;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// package-private cancelAfterRefundCheck()를 직접 호출하기 위해 service 패키지에 위치
@SpringBootTest
public class ReservationServiceCancelAfterRefundCheckTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;

    @Test
    @DisplayName("환불 미처리(refundHandled=false)인데 실제로는 결제가 완료돼 있으면, 조용히 취소하지 않고 재시도를 요구한다")
    void cancelAfterRefundCheck_paymentAppearedAfterCheck_throwsAndDoesNotCancel() {
        // given: PaymentService.hasCompletedPayment() 확인 시점 이후, 결제가 새로 완료된 상황을 재현
        Member member = TestFixtures.createAndSaveMember(memberRepository, "refundRaceUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, "race-payment-key");

        // when & then: refundHandled=false로 넘어왔지만 실제로는 결제가 있으므로 재시도 예외를 던짐
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancelAfterRefundCheck(member.getId(), reservationId, false));
        Assertions.assertTrue(exception.getMessage().contains("다시 시도"));

        // and: 예약은 여전히 CONFIRMED로 남아있고(조용히 취소되지 않음), 좌석도 그대로 유지됨
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }
}
