package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// 예매 오픈 때 한 좌석에 요청이 몰리면, 어차피 실패할 요청까지 좌석 락 앞에 줄을 서서 앞 요청이 끝나길 하나씩 기다렸음
// (300명이 한 좌석을 노리면 p95 약 500ms). 이미 팔린 좌석이면 락을 기다리지 않고 바로 거절해야 함
@SpringBootTest
public class SoldSeatFastRejectTest {

    private static final long LOCK_HOLD_MS = 3000;

    @Autowired private ReservationService reservationService;
    @Autowired private SeatRepository seatRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("이미 팔린 좌석은 다른 트랜잭션이 그 좌석 락을 잡고 있어도 기다리지 않고 바로 SEAT_ALREADY_RESERVED로 거절해야 한다")
    void soldSeat_rejectedWithoutWaitingForLock() throws InterruptedException {
        // given: 먼저 예매돼 팔린 좌석
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Member first = TestFixtures.createAndSaveMember(memberRepository, "fastRejectFirst");
        Member second = TestFixtures.createAndSaveMember(memberRepository, "fastRejectSecond");
        reservationService.reserve(first.getId(), List.of(seat.getId()));

        // 다른 트랜잭션(예: 같은 좌석을 노린 앞 요청)이 그 좌석 락을 3초 동안 잡고 있음
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        CountDownLatch lockedLatch = new CountDownLatch(1);
        executorService.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            seatRepository.findByIdInWithLock(List.of(seat.getId()));
            lockedLatch.countDown();
            sleepQuietly(LOCK_HOLD_MS);
        }));
        Assertions.assertTrue(lockedLatch.await(5, TimeUnit.SECONDS), "락을 잡는 스레드가 시작되지 않음");

        // when: 다른 회원이 같은 좌석을 예매
        long start = System.nanoTime();
        BusinessException e = Assertions.assertThrows(BusinessException.class,
                () -> reservationService.reserve(second.getId(), List.of(seat.getId())));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // then: 락이 풀릴 때까지(3초) 기다리지 않고 바로 거절
        Assertions.assertEquals(ErrorCode.SEAT_ALREADY_RESERVED, e.getErrorCode());
        Assertions.assertTrue(elapsedMs < 1000, "팔린 좌석인데 락을 기다렸음: " + elapsedMs + "ms");

        executorService.shutdown();
        Assertions.assertTrue(executorService.awaitTermination(10, TimeUnit.SECONDS));
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
