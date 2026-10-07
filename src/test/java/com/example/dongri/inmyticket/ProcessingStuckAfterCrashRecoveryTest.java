package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;

// PG 통신 도중 서버가 죽으면 processPayment()의 catch(PENDING 되돌리기)가 실행되지 않음.
// beginPaymentProcessing()만 호출하고 승인을 하지 않은 상태 = "PROCESSING 전환 직후 서버가 죽은 상태"로 재현
@SpringBootTest
public class ProcessingStuckAfterCrashRecoveryTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentService paymentService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;

    @Test
    @DisplayName("결제 시작 후 서버가 죽어 PROCESSING에 고착된 예약은 복구되어 다시 결제할 수 있다")
    void stuckProcessing_isRecoveredToPending_andCanBePaidAgain() {
        // given: 결제 시작(PROCESSING) 후 서버가 죽어 10분째 방치된 예약
        Member member = TestFixtures.createAndSaveMember(memberRepository, "crashUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        reservationService.beginPaymentProcessing(member.getId(), reservationId);

        Reservation stuck = reservationRepository.findById(reservationId).orElseThrow();
        stuck.setProcessingStartedAt(LocalDateTime.now().minusMinutes(10));
        reservationRepository.save(stuck);

        // when
        int recoveredCount = reservationService.recoverStuckProcessingReservations(LocalDateTime.now().minusMinutes(5));

        // then: PENDING으로 돌아오고, 사용자가 다시 결제하면 정상 확정됨
        Assertions.assertEquals(1, recoveredCount);
        Reservation recovered = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, recovered.getStatus());
        Assertions.assertNull(recovered.getProcessingStartedAt());

        paymentService.processPayment(member.getId(), reservationId, "retry-key-" + UUID.randomUUID());
        Assertions.assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("방금 결제를 시작한(PG 통신 중인) 예약은 복구 대상이 아니다")
    void inFlightProcessing_isNotRecovered() {
        // given: 방금 PROCESSING으로 전환된 예약
        Member member = TestFixtures.createAndSaveMember(memberRepository, "inFlightUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        reservationService.beginPaymentProcessing(member.getId(), reservationId);

        // when
        int recoveredCount = reservationService.recoverStuckProcessingReservations(LocalDateTime.now().minusMinutes(5));

        // then
        Assertions.assertEquals(0, recoveredCount);
        Assertions.assertEquals(ReservationStatus.PROCESSING,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
    }
}
