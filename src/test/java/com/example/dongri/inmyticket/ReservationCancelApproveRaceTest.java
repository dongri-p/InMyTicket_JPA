package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.PaymentStatus;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
public class ReservationCancelApproveRaceTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MemberRepository memberRepository;

    @Test
    @DisplayName("같은 예약에 대해 취소와 결제 승인이 동시에 발생해도 예약/좌석/결제 상태가 항상 일관되어야 한다")
    void cancelAndApprove_concurrently_endsInConsistentState() throws InterruptedException {
        // given
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Member member = new Member();
        member.setLoginId("raceUser" + suffix);
        member.setPassword("password123");
        member.setName("경쟁테스터");
        member.setEmail("race" + suffix + "@test.com");
        memberRepository.save(member);

        Schedule schedule = new Schedule();
        schedule.setStartTime(LocalDateTime.now().plusDays(1));
        schedule.setTotalSeatCount(1);
        schedule.setAvailableSeatCount(1);

        Seat seat = new Seat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setPrice(150000);
        schedule.addSeat(seat);
        scheduleRepository.save(schedule);

        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        // when: 취소와 결제 승인을 동시에 요청
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        executorService.submit(() -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                reservationService.cancelWithoutRefundCheck(member.getId(), reservationId);
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        executorService.submit(() -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                paymentApprovalService.approve(member.getId(), reservationId, "race-payment-key");
            } catch (Exception ignored) {
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        // then: 취소가 이겼든 결제가 이겼든, 예약/좌석/결제 상태는 항상 서로 일치해야 한다
        Reservation finalReservation = reservationRepository.findById(reservationId).orElseThrow();
        Seat finalSeat = seatRepository.findById(seat.getId()).orElseThrow();
        Schedule finalSchedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
        Payment finalPayment = finalReservation.getPayment();

        if (finalReservation.getStatus() == ReservationStatus.CANCELLED) {
            Assertions.assertEquals(SeatStatus.AVAILABLE, finalSeat.getStatus());
            Assertions.assertEquals(1, finalSchedule.getAvailableSeatCount());
            if (finalPayment != null) {
                Assertions.assertEquals(PaymentStatus.CANCELED, finalPayment.getStatus());
            }
        } else if (finalReservation.getStatus() == ReservationStatus.CONFIRMED) {
            Assertions.assertEquals(SeatStatus.RESERVED, finalSeat.getStatus());
            Assertions.assertEquals(0, finalSchedule.getAvailableSeatCount());
            Assertions.assertNotNull(finalPayment);
            Assertions.assertEquals(PaymentStatus.COMPLETED, finalPayment.getStatus());
        } else {
            Assertions.fail("예상치 못한 예약 상태: " + finalReservation.getStatus());
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
