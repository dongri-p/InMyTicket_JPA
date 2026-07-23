package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.OwnershipViolationException;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.PaymentStatus;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootTest
public class PaymentServiceCancelTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = TestFixtures.createAndSaveMember(memberRepository, "refundUser");
    }

    private Seat createSeat() {
        return TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
    }

    // Payment.paymentKey는 DB 유니크 제약이 있어, 같은 프로세스에서 여러 테스트가 공유 DB에
    // 동시에 결제를 승인해도 충돌하지 않도록 매번 새 키를 사용
    private String newPaymentKey() {
        return "test-payment-key-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("결제가 완료된 예약을 취소하면 PG 환불 통신 후 결제/좌석 상태가 취소로 반영된다")
    void processCancel_withCompletedPayment_refundsAndCancels() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, newPaymentKey());

        // when
        paymentService.processCancel(member.getId(), reservationId);

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Payment payment = reservation.getPayment();
        Assertions.assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        Assertions.assertNotNull(payment);
        Assertions.assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).get().getStatus());
    }

    @Test
    @DisplayName("결제 전(PENDING) 예약을 취소하면 환불 통신 없이 바로 취소된다")
    void processCancel_withoutPayment_cancelsWithoutRefundCall() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        // when
        paymentService.processCancel(member.getId(), reservationId);

        // then
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CANCELLED, reservation.getStatus());
        Assertions.assertNull(reservation.getPayment());
        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).get().getStatus());
    }

    @Test
    @DisplayName("타인의 결제완료 예약은 PG 환불 통신 없이 즉시 거부된다 (소유권 검증이 외부 통신보다 먼저 실행돼야 함)")
    void processCancel_byStranger_rejectedBeforeRefundCall() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, newPaymentKey());

        Member stranger = TestFixtures.createAndSaveMember(memberRepository, "refundStranger");

        // when & then
        long start = System.currentTimeMillis();
        Assertions.assertThrows(OwnershipViolationException.class,
                () -> paymentService.processCancel(stranger.getId(), reservationId));
        long elapsedMs = System.currentTimeMillis() - start;

        // PG 환불 통신(1.5초 시뮬레이션)이 실행되지 않고 즉시 거부됐는지 확인
        Assertions.assertTrue(elapsedMs < 1000, "소유권 검증 전에 외부 환불 통신이 실행된 것으로 보임 (elapsedMs=" + elapsedMs + ")");

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        Assertions.assertEquals(PaymentStatus.COMPLETED, reservation.getPayment().getStatus());
    }

    @Test
    @DisplayName("공연이 이미 시작된 결제완료 예약은 PG 환불 통신 없이 즉시 거부된다 (환불 나가고 취소는 거부되는 상황 방지)")
    void processCancel_afterScheduleStarted_rejectedBeforeRefundCall() {
        // given: 예매/결제 시점엔 공연 시작 전이었지만, 이후 시간이 지나 공연이 시작된 상황을 재현
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, newPaymentKey());

        Schedule startedSchedule = scheduleRepository.findById(seat.getSchedule().getId()).orElseThrow();
        startedSchedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(startedSchedule);

        // when & then
        long start = System.currentTimeMillis();
        Assertions.assertThrows(IllegalStateException.class,
                () -> paymentService.processCancel(member.getId(), reservationId));
        long elapsedMs = System.currentTimeMillis() - start;

        // PG 환불 통신(1.5초 시뮬레이션)이 실행되지 않고 즉시 거부됐는지 확인
        Assertions.assertTrue(elapsedMs < 1000, "공연 시작 검사 전에 외부 환불 통신이 실행된 것으로 보임 (elapsedMs=" + elapsedMs + ")");

        // and: 취소가 거부됐으므로 결제/예약 상태는 그대로 유지된다
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        Assertions.assertEquals(PaymentStatus.COMPLETED, reservation.getPayment().getStatus());
    }
}
