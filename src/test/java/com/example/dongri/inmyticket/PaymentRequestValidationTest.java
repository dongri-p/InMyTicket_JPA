package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
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

import java.util.UUID;

// 결제창 금액·주문번호는 프론트가 정해서 보내므로 조작될 수 있음.
// PG 승인 전에 예약 금액·예약 번호와 맞는지 확인하고, 맞지 않으면 결제를 시작하지 않는지 검증
@SpringBootTest
public class PaymentRequestValidationTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;

    @Test
    @DisplayName("결제 금액이 예약 금액과 다르면 거절하고 예약은 PENDING 그대로다")
    void amountMismatch_rejected() {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "amountUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        int totalPrice = reservationRepository.findById(reservationId).orElseThrow().getTotalPrice();

        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(member.getId(), reservationId, PgProvider.DEMO, "key-" + UUID.randomUUID(),
                        PaymentService.orderIdPrefix(reservationId) + "abc", totalPrice - 1000));

        Assertions.assertEquals("결제 금액이 예약 금액과 다릅니다.", thrown.getMessage());
        Assertions.assertEquals(ReservationStatus.PENDING,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
        Assertions.assertFalse(paymentRepository.findAll().stream()
                .anyMatch(p -> p.getReservation().getId().equals(reservationId)));
    }

    @Test
    @DisplayName("다른 예약의 주문번호로 결제하면 거절하고 예약은 PENDING 그대로다")
    void orderIdOfAnotherReservation_rejected() {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "orderIdUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        int totalPrice = reservationRepository.findById(reservationId).orElseThrow().getTotalPrice();

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> paymentService.processPayment(member.getId(), reservationId, PgProvider.DEMO, "key-" + UUID.randomUUID(),
                        PaymentService.orderIdPrefix(reservationId + 1) + "abc", totalPrice));

        Assertions.assertEquals(ReservationStatus.PENDING,
                reservationRepository.findById(reservationId).orElseThrow().getStatus());
    }
}
