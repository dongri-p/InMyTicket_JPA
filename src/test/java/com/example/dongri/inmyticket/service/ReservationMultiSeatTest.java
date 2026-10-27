package com.example.dongri.inmyticket.service;

import com.example.dongri.inmyticket.domain.BusinessException;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 여러 좌석을 예약 1건으로 묶는 예매 - 전부 성공하거나 전부 실패해야 하고, 취소도 좌석 전체를 되돌려야 함
// (package-private cancelWithoutRefundCheck()를 직접 호출하기 위해 service 패키지에 위치)
@SpringBootTest
public class ReservationMultiSeatTest {

    private static final int SEAT_COUNT = 3;

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Member member;
    private Schedule schedule;
    private List<Seat> seats;

    @BeforeEach
    void setUp() {
        member = TestFixtures.createAndSaveMember(memberRepository, "multiSeat");

        schedule = new Schedule();
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        schedule.setTotalSeatCount(SEAT_COUNT);
        schedule.setAvailableSeatCount(SEAT_COUNT);

        seats = new ArrayList<>();
        for (int i = 1; i <= SEAT_COUNT; i++) {
            Seat seat = new Seat();
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setSeatNumber(i);
            seat.setPrice(10000 * i);
            schedule.addSeat(seat);
            seats.add(seat);
        }
        scheduleRepository.save(schedule);
    }

    @Test
    @DisplayName("좌석 3개를 함께 예매하면 예약 1건에 티켓 3장, 총액은 좌석 가격의 합, 잔여석은 3 줄어든다")
    void reserveMultipleSeats_createsOneReservationWithAllTickets() {
        // when
        Long reservationId = reservationService.reserve(member.getId(), seatIds(seats));

        // then
        // tickets는 지연 로딩이라 트랜잭션 안에서 개수까지 읽어 둠
        int ticketCount = new TransactionTemplate(transactionManager).execute(status ->
                reservationRepository.findById(reservationId).get().getTickets().size());
        Assertions.assertEquals(SEAT_COUNT, ticketCount);
        Assertions.assertEquals(10000 + 20000 + 30000, reservationRepository.findById(reservationId).get().getTotalPrice());
        for (Seat seat : seats) {
            Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());
        }
        Assertions.assertEquals(0, availableSeatCount());
    }

    @Test
    @DisplayName("고른 좌석 중 하나라도 이미 예약됐으면 나머지 좌석도 예약되지 않는다")
    void reserveMultipleSeats_oneTaken_reservesNothing() {
        // given: 세 번째 좌석을 다른 회원이 먼저 예매
        Member other = TestFixtures.createAndSaveMember(memberRepository, "multiSeatOther");
        reservationService.reserve(other.getId(), seats.get(2).getId());

        // when & then
        Assertions.assertThrows(BusinessException.class,
                () -> reservationService.reserve(member.getId(), seatIds(seats)));

        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seats.get(0).getId()).get().getStatus());
        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seats.get(1).getId()).get().getStatus());
        Assertions.assertEquals(SEAT_COUNT - 1, availableSeatCount());
        Assertions.assertTrue(reservationRepository.findAll().stream()
                .noneMatch(reservation -> reservation.getMember().getId().equals(member.getId())));
    }

    @Test
    @DisplayName("다른 회차의 좌석을 섞어서 예매할 수 없다")
    void reserveSeatsFromDifferentSchedules_isRejected() {
        // given
        Seat otherScheduleSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        // when & then
        Assertions.assertThrows(BusinessException.class,
                () -> reservationService.reserve(member.getId(), List.of(seats.get(0).getId(), otherScheduleSeat.getId())));

        Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seats.get(0).getId()).get().getStatus());
        Assertions.assertEquals(SEAT_COUNT, availableSeatCount());
    }

    @Test
    @DisplayName("같은 좌석을 중복으로 보내면 거부한다")
    void reserveDuplicateSeatIds_isRejected() {
        Long seatId = seats.get(0).getId();

        Assertions.assertThrows(BusinessException.class,
                () -> reservationService.reserve(member.getId(), List.of(seatId, seatId)));
        Assertions.assertEquals(SEAT_COUNT, availableSeatCount());
    }

    @Test
    @DisplayName("여러 좌석 예약을 취소하면 좌석 전부와 잔여석이 원상복구된다")
    void cancelMultiSeatReservation_releasesAllSeats() {
        // given
        Long reservationId = reservationService.reserve(member.getId(), seatIds(seats));

        // when
        reservationService.cancelWithoutRefundCheck(member.getId(), reservationId);

        // then
        for (Seat seat : seats) {
            Assertions.assertEquals(SeatStatus.AVAILABLE, seatRepository.findById(seat.getId()).get().getStatus());
        }
        Assertions.assertEquals(SEAT_COUNT, availableSeatCount());
    }

    private List<Long> seatIds(List<Seat> seatList) {
        return seatList.stream().map(Seat::getId).toList();
    }

    private int availableSeatCount() {
        return scheduleRepository.findById(schedule.getId()).get().getAvailableSeatCount();
    }
}
