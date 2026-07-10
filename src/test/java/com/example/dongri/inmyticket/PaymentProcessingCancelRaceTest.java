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
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.atomic.AtomicReference;

// PaymentService.processPayment()의 실제 진입점(beginPaymentProcessing -> PG통신 -> approve)을
// PaymentService.processCancel()과 경합시켜, PROCESSING 취소 차단(11차 후속 수정)이
// 실제로 "PG는 성공했는데 결제 기록이 없는" 상황을 막는지 종단으로 검증
@SpringBootTest
public class PaymentProcessingCancelRaceTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ReservationRepository reservationRepository;

    @Test
    @DisplayName("결제 진행 중 취소 요청이 들어와도 취소는 거부되고 결제는 정상 완료된다")
    void payAndCancel_racing_paymentWinsAndCancelIsRejected() throws InterruptedException {
        // given
        Member member = TestFixtures.createAndSaveMember(memberRepository, "payCancelRaceUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        AtomicReference<Exception> cancelException = new AtomicReference<>();
        AtomicReference<Long> paymentId = new AtomicReference<>();
        AtomicReference<Exception> paymentException = new AtomicReference<>();

        // when: 결제 시작(내부적으로 PENDING -> PROCESSING 전환 후 1.5초 PG 통신)
        Thread paymentThread = new Thread(() -> {
            try {
                paymentId.set(paymentService.processPayment(member.getId(), reservationId, "race-payment-key"));
            } catch (Exception e) {
                paymentException.set(e);
            }
        });
        paymentThread.start();

        // 결제 스레드가 beginPaymentProcessing()을 마치고 PG 통신(1.5초) 중일 시점을 노려 취소 시도
        Thread.sleep(300);

        Thread cancelThread = new Thread(() -> {
            try {
                paymentService.processCancel(member.getId(), reservationId);
            } catch (Exception e) {
                cancelException.set(e);
            }
        });
        cancelThread.start();
        cancelThread.join();
        paymentThread.join();

        // then: 취소는 "결제가 진행 중이라 취소할 수 없습니다" 예외로 거부되고
        Assertions.assertNotNull(cancelException.get(), "PROCESSING 중 취소는 거부되어야 함");
        Assertions.assertInstanceOf(IllegalStateException.class, cancelException.get());

        // 결제는 방해 없이 정상 완료된다
        Assertions.assertNull(paymentException.get(), "결제는 취소 시도와 무관하게 성공해야 함");
        Assertions.assertNotNull(paymentId.get());

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
        Assertions.assertEquals(SeatStatus.RESERVED, seatRepository.findById(seat.getId()).get().getStatus());
    }
}
