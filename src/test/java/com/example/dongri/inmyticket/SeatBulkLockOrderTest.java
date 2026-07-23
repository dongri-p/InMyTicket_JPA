package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.domain.SeatStatus;
import com.example.dongri.inmyticket.repository.SeatRepository;

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
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest
public class SeatBulkLockOrderTest {

    @Autowired private SeatRepository seatRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("겹치는 좌석 집합을 서로 반대 순서로 요청해도 findByIdInWithLock의 ORDER BY 덕분에 데드락 없이 순차 완료되어야 한다")
    void findByIdInWithLock_reverseOrderRequests_noDeadlock() throws InterruptedException {
        // given: id 순서를 알 수 있는 좌석 두 개
        Seat seatA = new Seat();
        seatA.setStatus(SeatStatus.AVAILABLE);
        seatRepository.save(seatA);

        Seat seatB = new Seat();
        seatB.setStatus(SeatStatus.AVAILABLE);
        seatRepository.save(seatB);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Exception> thread1Error = new AtomicReference<>();
        AtomicReference<Exception> thread2Error = new AtomicReference<>();

        // 정방향(A, B) 순서로 요청
        executorService.submit(() -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    seatRepository.findByIdInWithLock(List.of(seatA.getId(), seatB.getId()));
                    sleepQuietly(300);
                });
            } catch (Exception e) {
                thread1Error.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // 역방향(B, A) 순서로 요청 — ORDER BY가 없다면 반대 순서로 락을 잡아 데드락 위험
        executorService.submit(() -> {
            readyLatch.countDown();
            await(startLatch);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    seatRepository.findByIdInWithLock(List.of(seatB.getId(), seatA.getId()));
                    sleepQuietly(300);
                });
            } catch (Exception e) {
                thread2Error.set(e);
            } finally {
                doneLatch.countDown();
            }
        });

        readyLatch.await();
        startLatch.countDown();
        boolean finishedInTime = doneLatch.await(10, TimeUnit.SECONDS);

        // then: 10초 안에 데드락 없이 (한쪽이 대기하다가) 순차적으로 완료되어야 한다
        Assertions.assertTrue(finishedInTime, "두 트랜잭션이 10초 내에 데드락 없이 완료되어야 한다");
        Assertions.assertNull(thread1Error.get(), "정방향 요청 스레드에서 예외 발생: " + thread1Error.get());
        Assertions.assertNull(thread2Error.get(), "역방향 요청 스레드에서 예외 발생: " + thread2Error.get());

        executorService.shutdown();
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
