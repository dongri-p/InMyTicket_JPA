package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.ReservationService;

import org.junit.jupiter.api.Assertions; // 🌟 임포트 꼬일 일 없는 순수 JUnit 5
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class ReservationServiceTest {

    @Autowired private ReservationService reservationService;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private SeatRepository seatRepository;
    @Autowired private MemberRepository memberRepository;

    @Test
    @DisplayName("동시에 100명이 딱 하나 남은 좌석을 예매하려고 광클해도, 정확히 1명만 성공해야 한다.")
    public void 동시에_100명_티켓팅_테스트() throws InterruptedException {
        // given
        Member member = new Member();
        member.setLoginId("testUser");       // login_id가 null이 아니도록 채워주기!
        member.setPassword("password123");
        member.setName("테스터");
        member.setEmail("test@test.com");
        memberRepository.save(member);

        Seat seat = new Seat();
        seat.setStatus(SeatStatus.AVAILABLE);
        seatRepository.save(seat);

        long reservationCountBefore = reservationRepository.count();

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    reservationService.reserve(member.getId(), seat.getId());
                    successCount.incrementAndGet(); 
                } catch (Exception e) {
                    failedCount.incrementAndGet(); 
                } finally {
                    latch.countDown(); 
                }
            });
        }

        latch.await(); 

        // then: AssertJ 대신 순수 JUnit 5로 철저하게 검증!
        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("티켓팅 성공 인원: " + successCount.get());
        System.out.println("티켓팅 실패(튕김) 인원: " + failedCount.get());

        // 기대하는값(1), 실제결과값 순서로 넣는 정석 JUnit 5 문법
        Assertions.assertEquals(1, successCount.get());
        // 다른 테스트가 같은 컨텍스트에서 먼저 만들어둔 예약 행이 섞여도 이 테스트가 만든 증가분(1건)만 검증
        Assertions.assertEquals(reservationCountBefore + 1, reservationRepository.count());
    }
}