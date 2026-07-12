package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class PaymentApprovalServiceTest {

    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PaymentRepository paymentRepository;

    // Payment.paymentKey는 DB 유니크 제약이 있어, 같은 프로세스에서 여러 테스트가 공유 DB에
    // 동시에 결제를 승인해도 충돌하지 않도록 매번 새 키를 사용
    private String newPaymentKey() {
        return "test-payment-key-" + UUID.randomUUID();
    }

    @Test
    @DisplayName("같은 예약에 대해 결제 승인을 동시에 여러 번 요청해도, 정확히 1건만 성공해야 한다.")
    public void 동시_결제_중복승인_테스트() throws InterruptedException {
        // given
        Member member = TestFixtures.createAndSaveMember(memberRepository, "payTestUser");

        Reservation reservation = new Reservation();
        reservation.setMember(member);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setTotalPrice(150000);
        reservation.setReservedAt(LocalDateTime.now());
        reservationRepository.save(reservation);

        long paymentCountBefore = paymentRepository.count();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();
        String paymentKey = newPaymentKey();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentApprovalService.approve(member.getId(), reservation.getId(), paymentKey);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failedCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // then
        System.out.println("=== 중복 결제 승인 테스트 결과 ===");
        System.out.println("결제 성공 횟수: " + successCount.get());
        System.out.println("결제 실패(거부) 횟수: " + failedCount.get());

        Assertions.assertEquals(1, successCount.get());
        // 다른 테스트가 같은 컨텍스트에서 먼저 만들어둔 결제 행이 섞여도 이 테스트가 만든 증가분(1건)만 검증
        Assertions.assertEquals(paymentCountBefore + 1, paymentRepository.count());
    }

    @Test
    @DisplayName("공연이 이미 시작된 예약은 approve()를 직접 호출해도 결제를 승인할 수 없다")
    void approve_afterScheduleStarted_throwsIllegalState() {
        // given: 예매 시점엔 공연 시작 전이었지만, 이후 시간이 지나 공연이 시작된 상황을 재현.
        // beginPaymentProcessing()을 거치지 않고 approve()를 바로 호출해, 이 메서드 자체의 최종 방어선을 검증
        Member member = TestFixtures.createAndSaveMember(memberRepository, "approveStartedUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        Schedule startedSchedule = scheduleRepository.findById(seat.getSchedule().getId()).orElseThrow();
        startedSchedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(startedSchedule);

        // when & then
        Assertions.assertThrows(IllegalStateException.class,
                () -> paymentApprovalService.approve(member.getId(), reservationId, newPaymentKey()));

        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, reservation.getStatus());
        Assertions.assertNull(reservation.getPayment());
    }

    @Test
    @DisplayName("서로 다른 예약에 같은 paymentKey로 결제를 승인하면 두 번째는 거부된다")
    void approve_withDuplicatePaymentKey_secondRejected() {
        // given
        Member member = TestFixtures.createAndSaveMember(memberRepository, "dupKeyUser");
        Seat seatA = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Seat seatB = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationIdA = reservationService.reserve(member.getId(), seatA.getId());
        Long reservationIdB = reservationService.reserve(member.getId(), seatB.getId());
        String duplicateKey = newPaymentKey();

        paymentApprovalService.approve(member.getId(), reservationIdA, duplicateKey);

        // when & then
        Assertions.assertThrows(DataIntegrityViolationException.class,
                () -> paymentApprovalService.approve(member.getId(), reservationIdB, duplicateKey));

        Reservation reservationB = reservationRepository.findById(reservationIdB).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, reservationB.getStatus());
        Assertions.assertNull(reservationB.getPayment());
    }
}
