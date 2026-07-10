package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class PaymentApprovalServiceTest {

    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;

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

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    paymentApprovalService.approve(member.getId(), reservation.getId(), "test-payment-key");
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
}
