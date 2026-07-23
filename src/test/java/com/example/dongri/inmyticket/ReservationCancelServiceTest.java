package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.OwnershipViolationException;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
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
public class ReservationCancelServiceTest {

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MemberRepository memberRepository;

    private Member owner;
    private Member stranger;
    private Schedule schedule;
    private Seat seat;

    @BeforeEach
    void setUp() {
        owner = TestFixtures.createAndSaveMember(memberRepository, "cancelOwner");
        stranger = TestFixtures.createAndSaveMember(memberRepository, "cancelStranger");

        seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        schedule = seat.getSchedule();
    }

    @Test
    @DisplayName("예약을 취소하면 좌석과 잔여 좌석 수가 원상복구된다")
    void cancel_releasesSeatAndRestoresCount() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());
        Assertions.assertEquals(0, scheduleRepository.findById(schedule.getId()).get().getAvailableSeatCount());

        // when
        reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId);

        // then
        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).get().getStatus());
        Assertions.assertEquals(1, scheduleRepository.findById(schedule.getId()).get().getAvailableSeatCount());
    }

    @Test
    @DisplayName("본인의 예약이 아니면 취소할 수 없다")
    void cancel_byStranger_throwsAccessDenied() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());

        // when & then
        Assertions.assertThrows(OwnershipViolationException.class,
                () -> reservationService.cancelWithoutRefundCheck(stranger.getId(), reservationId));
    }

    @Test
    @DisplayName("이미 취소된 예약은 다시 취소할 수 없다")
    void cancel_alreadyCancelled_throwsIllegalState() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());
        reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId));
    }

    @Test
    @DisplayName("결제 승인 통신이 진행 중(PROCESSING)인 예약은 취소할 수 없다")
    void cancel_whileProcessingPayment_throwsIllegalState() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());
        reservationService.beginPaymentProcessing(owner.getId(), reservationId);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId));

        // and: 취소가 거부됐으므로 좌석/잔여석은 그대로 유지된다
        Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());
        Assertions.assertEquals(0, scheduleRepository.findById(schedule.getId()).get().getAvailableSeatCount());
    }

    @Test
    @DisplayName("공연이 이미 시작된 회차의 좌석은 새로 예매할 수 없다")
    void reserve_afterScheduleStarted_throwsIllegalState() {
        // given
        Seat startedSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository, LocalDateTime.now().minusMinutes(1));

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.reserve(owner.getId(), startedSeat.getId()));
    }

    @Test
    @DisplayName("공연이 이미 시작된 예약은 취소할 수 없다")
    void cancel_afterScheduleStarted_throwsIllegalState() {
        // given: 예매 시점엔 공연 시작 전이었지만, 이후 시간이 지나 공연이 시작된 상황을 재현
        Seat startedSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(owner.getId(), startedSeat.getId());

        // reserve()가 다른 트랜잭션에서 좌석을 변경했으므로, 새로 조회한 관리 상태의 엔티티에 반영해야
        // cascade로 묶인 Seat의 버전 불일치(OptimisticLockException) 없이 저장된다
        Schedule startedSchedule = scheduleRepository.findById(startedSeat.getSchedule().getId()).orElseThrow();
        startedSchedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(startedSchedule);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId));
    }

    @Test
    @DisplayName("공연이 이미 시작된 예약은 결제를 시작할 수 없다")
    void beginPaymentProcessing_afterScheduleStarted_throwsIllegalState() {
        // given: 예매 시점엔 공연 시작 전이었지만, 이후 시간이 지나 공연이 시작된 상황을 재현
        Seat startedSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(owner.getId(), startedSeat.getId());

        Schedule startedSchedule = scheduleRepository.findById(startedSeat.getSchedule().getId()).orElseThrow();
        startedSchedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(startedSchedule);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.beginPaymentProcessing(owner.getId(), reservationId));

        // and: 결제 시작이 거부됐으므로 예약 상태는 PENDING 그대로 유지된다
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, reservation.getStatus());
    }
}
