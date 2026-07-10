package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
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
import org.springframework.security.access.AccessDeniedException;

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
        Assertions.assertThrows(AccessDeniedException.class,
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
    @DisplayName("공연이 이미 시작된 예약은 취소할 수 없다")
    void cancel_afterScheduleStarted_throwsIllegalState() {
        // given
        Seat startedSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository, LocalDateTime.now().minusMinutes(1));

        Long reservationId = reservationService.reserve(owner.getId(), startedSeat.getId());

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancelWithoutRefundCheck(owner.getId(), reservationId));
    }
}
