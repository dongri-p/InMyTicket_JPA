package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

// approve() 실패 시 예약이 PROCESSING에 영구히 갇히지 않고 PENDING으로 돌아가는지 검증(13차 발견)
@SpringBootTest
public class PaymentProcessingStuckRecoveryTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    @DisplayName("PG 통신 이후 approve()가 실패해도 예약은 PROCESSING에 갇히지 않고 PENDING으로 되돌아간다")
    void processPayment_whenApproveFails_revertsToPendingInsteadOfStuck() {
        // given
        Member member = TestFixtures.createAndSaveMember(memberRepository, "stuckRecoveryUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        // 이 예약에 이미 결제(Payment)가 있는 상태를 인위적으로 만들어, approve()가
        // reservation_id unique 제약 위반(DataIntegrityViolationException)으로 실패하도록 유도
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        paymentRepository.save(Payment.createPayment(reservation, reservation.getTotalPrice(), "pre-existing-key-" + UUID.randomUUID()));

        // when & then: processPayment()는 실패하고
        Assertions.assertThrows(RuntimeException.class,
                () -> paymentService.processPayment(member.getId(), reservationId, "new-key-" + UUID.randomUUID()));

        // 예약은 PROCESSING에 갇히지 않고 PENDING으로 되돌아가 있어야 함
        Reservation result = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, result.getStatus());
    }
}
