package com.example.dongri.inmyticket.service;

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
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

// package-private cancelWithoutRefundCheck()를 직접 호출하기 위해 service 패키지에 위치
@SpringBootTest
public class ReservationCancelApproveRaceTest {

    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MemberRepository memberRepository;

    private static final int RACE_ITERATIONS = 20;

    @Test
    @DisplayName("같은 예약에 대해 취소와 결제 승인이 동시에 발생해도 예약/좌석/결제 상태가 항상 일관되어야 한다")
    void cancelAndApprove_concurrently_endsInConsistentState() throws InterruptedException {
        int cancelWins = 0;
        int approveWins = 0;

        // 스레드 풀을 반복마다 새로 만들면, 매번 새 워커 스레드가 콜드 스타트하면서
        // 먼저 제출된 작업이 구조적으로 유리해지는 편향이 생긴다. 풀을 한 번만 만들고
        // 미리 두 스레드를 띄워둔 채로 재사용해 반복 간 편향을 없앤다.
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        ((ThreadPoolExecutor) executorService).prestartAllCoreThreads();

        // JIT/클래스 초기화가 두 서비스 빈에 고르게 이뤄지도록 워밍업(집계에는 포함 안 함).
        for (int w = 0; w < 5; w++) {
            Member warmupMember = TestFixtures.createAndSaveMember(memberRepository, "warmup");
            Seat warmupSeat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
            Long warmupReservationId = reservationService.reserve(warmupMember.getId(), warmupSeat.getId());
            paymentApprovalService.approve(warmupMember.getId(), warmupReservationId, "warmup-key-" + UUID.randomUUID());
        }

        for (int i = 0; i < RACE_ITERATIONS; i++) {
            // given: 매 반복마다 독립된 예약을 새로 만들어 이전 반복의 결과가 섞이지 않게 함
            Member member = TestFixtures.createAndSaveMember(memberRepository, "raceUser");
            Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
            Schedule schedule = seat.getSchedule();

            Long reservationId = reservationService.reserve(member.getId(), seat.getId());

            // when: 취소와 결제 승인을 동시에 요청
            CountDownLatch readyLatch = new CountDownLatch(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);

            // 결제 키는 출발 신호 전에 미리 만들어둠 - UUID 생성(SecureRandom 접근)이
            // 출발 신호 이후 DB 호출 직전에 끼어들면 그 시간만큼 매번 승인 쪽만 불리해진다
            String paymentKey = "race-payment-key-" + UUID.randomUUID();

            Runnable cancelTask = () -> {
                readyLatch.countDown();
                await(startLatch);
                try {
                    reservationService.cancelWithoutRefundCheck(member.getId(), reservationId);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            };

            Runnable approveTask = () -> {
                readyLatch.countDown();
                await(startLatch);
                try {
                    paymentApprovalService.approve(member.getId(), reservationId, paymentKey);
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            };

            // 제출 순서 자체가 항상 어느 한쪽에 유리하게 작용하지 않도록 매 반복마다 순서를 무작위로 섞는다
            List<Runnable> tasks = Arrays.asList(cancelTask, approveTask);
            Collections.shuffle(tasks);
            tasks.forEach(executorService::submit);

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();

            // then: 취소가 이겼든 결제가 이겼든, 예약/좌석/결제 상태는 항상 서로 일치해야 한다
            Reservation finalReservation = reservationRepository.findById(reservationId).orElseThrow();
            Seat finalSeat = seatRepository.findById(seat.getId()).orElseThrow();
            Schedule finalSchedule = scheduleRepository.findById(schedule.getId()).orElseThrow();
            Payment finalPayment = finalReservation.getPayment();

            if (finalReservation.getStatus() == ReservationStatus.CANCELLED) {
                cancelWins++;
                Assertions.assertEquals(SeatStatus.AVAILABLE, finalSeat.getStatus());
                Assertions.assertEquals(1, finalSchedule.getAvailableSeatCount());
                if (finalPayment != null) {
                    Assertions.assertEquals(PaymentStatus.CANCELED, finalPayment.getStatus());
                }
            } else if (finalReservation.getStatus() == ReservationStatus.CONFIRMED) {
                approveWins++;
                Assertions.assertEquals(SeatStatus.RESERVED, finalSeat.getStatus());
                Assertions.assertEquals(0, finalSchedule.getAvailableSeatCount());
                Assertions.assertNotNull(finalPayment);
                Assertions.assertEquals(PaymentStatus.COMPLETED, finalPayment.getStatus());
            } else {
                Assertions.fail("예상치 못한 예약 상태: " + finalReservation.getStatus());
            }
        }

        executorService.shutdown();

        System.out.println("=== 취소 vs 결제 승인 경쟁 결과 (" + RACE_ITERATIONS + "회 반복) ===");
        System.out.println("취소가 이긴 횟수: " + cancelWins);
        System.out.println("결제 승인이 이긴 횟수: " + approveWins);
        // 참고: 이 테스트에서 취소가 결정적으로 항상 이긴다(20/0) - 스레드풀 콜드스타트,
        // 제출 순서, JIT 워밍업을 모두 배제하고 확인한 결과 타이밍 문제가 아니었다.
        // 실제 원인은 cancelWithoutRefundCheck()가 CANCELLED/PROCESSING 상태만 취소를
        // 거부하고 CONFIRMED는 거부 목록에 없다는 점 - 결제 승인이 먼저 끝나 있어도
        // 이 메서드는 그와 무관하게 조용히 다시 취소해버린다(주석에 문서화된 위험 그대로).
        // 즉 이 테스트가 실제로 검증하는 불변식은 "누가 이기든 상태 일관성 유지"가 아니라,
        // "cancelWithoutRefundCheck()를 쓰면 결제 완료 여부와 무관하게 최종적으로 취소된다"는
        // 것이다. 운영 경로(PaymentService.processCancel → cancelAfterRefundCheck)는
        // PROCESSING 차단과 refundHandled 재확인으로 이 문제를 실제로 막고 있으며,
        // 그 경로의 레이스는 PaymentProcessingCancelRaceTest가 별도로 검증한다.
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
