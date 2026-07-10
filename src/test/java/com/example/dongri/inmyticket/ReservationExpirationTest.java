package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

@SpringBootTest
public class ReservationExpirationTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        member = TestFixtures.createAndSaveMember(memberRepository, "expireUser");
    }

    private Seat createSeat() {
        return TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
    }

    @Test
    @DisplayName("결제 없이 방치 시간이 지난 PENDING 예약은 자동으로 취소되고 좌석이 회수된다")
    void expireStalePendingReservations_releasesAbandonedSeat() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setReservedAt(LocalDateTime.now().minusMinutes(20));
        reservationRepository.save(reservation);

        // when
        int expiredCount = reservationService.expireStalePendingReservations(LocalDateTime.now().minusMinutes(10));

        // then
        Assertions.assertEquals(1, expiredCount);
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CANCELLED, result.getStatus());
        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).get().getStatus());
        Assertions.assertEquals(1, scheduleRepository.findById(seat.getSchedule().getId()).get().getAvailableSeatCount());
    }

    @Test
    @DisplayName("방치 시간이 아직 안 지난 PENDING 예약은 자동 취소 대상이 아니다")
    void expireStalePendingReservations_ignoresFreshPending() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        // when
        int expiredCount = reservationService.expireStalePendingReservations(LocalDateTime.now().minusMinutes(10));

        // then
        Assertions.assertEquals(0, expiredCount);
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, result.getStatus());
        Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());
    }

    @Test
    @DisplayName("PG 통신 등으로 결제 진행 중(PROCESSING)인 예약은 방치 시간이 지나도 자동 취소 대상이 아니다")
    void expireStalePendingReservations_ignoresProcessingReservation() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        reservationService.beginPaymentProcessing(member.getId(), reservationId);

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setReservedAt(LocalDateTime.now().minusMinutes(20));
        reservationRepository.save(reservation);

        // when
        int expiredCount = reservationService.expireStalePendingReservations(LocalDateTime.now().minusMinutes(10));

        // then
        Assertions.assertEquals(0, expiredCount);
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PROCESSING, result.getStatus());
        Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());

        // PG 통신 실패 시나리오: PROCESSING -> PENDING으로 되돌리면 다시 자동만료 대상이 된다
        reservationService.revertProcessingToPending(reservationId);
        int expiredAfterRevert = reservationService.expireStalePendingReservations(LocalDateTime.now().minusMinutes(10));
        Assertions.assertEquals(1, expiredAfterRevert);
        Assertions.assertEquals(ReservationStatus.CANCELLED, reservationRepository.findById(reservationId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("결제가 완료된 예약은 방치 시간이 지나도 자동 취소 대상이 아니다")
    void expireStalePendingReservations_ignoresConfirmedReservation() {
        // given
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key");

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setReservedAt(LocalDateTime.now().minusMinutes(20));
        reservationRepository.save(reservation);

        // when
        int expiredCount = reservationService.expireStalePendingReservations(LocalDateTime.now().minusMinutes(10));

        // then
        Assertions.assertEquals(0, expiredCount);
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, result.getStatus());
        Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());
    }
}
