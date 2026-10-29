package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.PgProvider;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class ReservationExpirationTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private PlatformTransactionManager transactionManager;

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
        Assertions.assertEquals(1, TestFixtures.availableSeatCount(seatRepository, seat.getSchedule().getId()));
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
        reservationService.beginPaymentProcessing(member.getId(), reservationId, reservationRepository.findById(reservationId).orElseThrow().getTotalPrice());

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
        paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key-" + UUID.randomUUID(), PgProvider.DEMO);

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

    @Test
    @DisplayName("후보 조회 후 다른 트랜잭션이 결제를 확정하면, 락 재조회는 확정된 최신 상태를 봐야 한다")
    void lockedReload_seesStatusCommittedByOtherTransaction() {
        // given - 방치 시간이 지난 PENDING 예약
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setReservedAt(LocalDateTime.now().minusMinutes(20));
        reservationRepository.save(reservation);

        TransactionTemplate schedulerTx = new TransactionTemplate(transactionManager);
        TransactionTemplate paymentTx = new TransactionTemplate(transactionManager);
        paymentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when - expireStalePendingReservations()와 같은 순서를 한 트랜잭션 안에서 재현
        // (후보를 엔티티로 조회하던 이전 구현에서는 1)에서 캐시된 PENDING 엔티티가 3)에서 그대로 반환되어 실패했음)
        ReservationStatus seenStatus = schedulerTx.execute(status -> {
            // 1) 락 없이 후보 id 조회
            List<Long> candidateIds = reservationRepository.findIdsByStatusAndReservedAtBefore(ReservationStatus.PENDING, LocalDateTime.now().minusMinutes(10));
            Assertions.assertTrue(candidateIds.contains(reservationId));

            // 2) 그 사이 다른 트랜잭션(별도 영속성 컨텍스트)이 결제 승인 후 커밋
            paymentTx.executeWithoutResult(s ->
                    paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key-" + UUID.randomUUID(), PgProvider.DEMO));

            // 3) 락을 잡고 재조회 - DB에는 CONFIRMED가 있음
            List<Reservation> locked = reservationRepository.findByIdInWithLock(List.of(reservationId));
            return locked.get(0).getStatus();
        });

        // then
        Assertions.assertEquals(ReservationStatus.CONFIRMED, seenStatus);
    }

    @Test
    @DisplayName("PROCESSING 고착 후보 조회 후 결제가 확정되면, 락 재조회는 확정된 최신 상태를 봐야 한다")
    void lockedReload_seesConfirmedAfterProcessingCandidateQuery() {
        // given - 결제 시작(PROCESSING) 후 오래 지난 예약
        Seat seat = createSeat();
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        reservationService.beginPaymentProcessing(member.getId(), reservationId, reservationRepository.findById(reservationId).orElseThrow().getTotalPrice());
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setProcessingStartedAt(LocalDateTime.now().minusMinutes(20));
        reservationRepository.save(reservation);

        TransactionTemplate schedulerTx = new TransactionTemplate(transactionManager);
        TransactionTemplate paymentTx = new TransactionTemplate(transactionManager);
        paymentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // when - recoverStuckProcessingReservations()와 같은 순서
        ReservationStatus seenStatus = schedulerTx.execute(status -> {
            List<Long> candidateIds = reservationRepository.findIdsByStatusAndProcessingStartedAtBefore(ReservationStatus.PROCESSING, LocalDateTime.now().minusMinutes(10));
            Assertions.assertTrue(candidateIds.contains(reservationId));

            paymentTx.executeWithoutResult(s ->
                    paymentApprovalService.approve(member.getId(), reservationId, "test-payment-key-" + UUID.randomUUID(), PgProvider.DEMO));

            return reservationRepository.findByIdInWithLock(List.of(reservationId)).get(0).getStatus();
        });

        // then - CONFIRMED로 보여야 PENDING으로 되돌리지 않음
        Assertions.assertEquals(ReservationStatus.CONFIRMED, seenStatus);
    }
}
