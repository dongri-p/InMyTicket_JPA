package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;

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
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        member = new Member();
        member.setLoginId("refundUser" + suffix);
        member.setPassword("password123");
        member.setName("환불테스터");
        member.setEmail("refund" + suffix + "@test.com");
        memberRepository.save(member);
    }

    private Seat createSeat() {
        Schedule schedule = new Schedule();
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        schedule.setTotalSeatCount(1);
        schedule.setAvailableSeatCount(1);

        Seat seat = new Seat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setPrice(150000);
        schedule.addSeat(seat);

        scheduleRepository.save(schedule);
        return seat;
    }

    @Test
    @DisplayName("결제가 완료된 예약을 취소하면 PG 환불 통신 후 결제/좌석 상태가 취소로 반영된다")
    void processCancel_withCompletedPayment_refundsAndCancels() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key");

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
        paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key");

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member stranger = new Member();
        stranger.setLoginId("refundStranger" + suffix);
        stranger.setPassword("password123");
        stranger.setName("타인");
        stranger.setEmail("refundStranger" + suffix + "@test.com");
        memberRepository.save(stranger);

        // when & then
        long start = System.currentTimeMillis();
        Assertions.assertThrows(AccessDeniedException.class,
                () -> paymentService.processCancel(stranger.getId(), reservationId));
        long elapsedMs = System.currentTimeMillis() - start;

        // PG 환불 통신(1.5초 시뮬레이션)이 실행되지 않고 즉시 거부됐는지 확인
        Assertions.assertTrue(elapsedMs < 1000, "소유권 검증 전에 외부 환불 통신이 실행된 것으로 보임 (elapsedMs=" + elapsedMs + ")");

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        Assertions.assertEquals(PaymentStatus.COMPLETED, reservation.getPayment().getStatus());
    }
}
