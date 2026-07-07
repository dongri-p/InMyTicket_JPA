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
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        owner = new Member();
        owner.setLoginId("cancelOwner" + suffix);
        owner.setPassword("password123");
        owner.setName("취소테스터");
        owner.setEmail("cancelOwner" + suffix + "@test.com");
        memberRepository.save(owner);

        stranger = new Member();
        stranger.setLoginId("cancelStranger" + suffix);
        stranger.setPassword("password123");
        stranger.setName("타인");
        stranger.setEmail("cancelStranger" + suffix + "@test.com");
        memberRepository.save(stranger);

        schedule = new Schedule();
        schedule.setStartTime(LocalDateTime.now());
        schedule.setTotalSeatCount(1);
        schedule.setAvailableSeatCount(1);

        seat = new Seat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setPrice(150000);
        schedule.addSeat(seat);

        scheduleRepository.save(schedule);
    }

    @Test
    @DisplayName("예약을 취소하면 좌석과 잔여 좌석 수가 원상복구된다")
    void cancel_releasesSeatAndRestoresCount() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());
        Assertions.assertEquals(0, scheduleRepository.findById(schedule.getId()).get().getAvailableSeatCount());

        // when
        reservationService.cancel(owner.getId(), reservationId);

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
                () -> reservationService.cancel(stranger.getId(), reservationId));
    }

    @Test
    @DisplayName("이미 취소된 예약은 다시 취소할 수 없다")
    void cancel_alreadyCancelled_throwsIllegalState() {
        // given
        Long reservationId = reservationService.reserve(owner.getId(), seat.getId());
        reservationService.cancel(owner.getId(), reservationId);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> reservationService.cancel(owner.getId(), reservationId));
    }
}
