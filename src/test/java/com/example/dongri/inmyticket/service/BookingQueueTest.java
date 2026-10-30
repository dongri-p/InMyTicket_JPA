package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.example.dongri.inmyticket.api.dto.QueueStatusDto;
import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.support.TestFixtures;

// 예매 대기열: 실제 Redis(컨테이너)로 순번·입장·예매 관문을 확인
// 스케줄러가 끼어들지 않게 입장 주기를 길게 두고, 입장은 테스트에서 admitAll()로 직접 한 번씩 돌림 (한 번에 2명)
@Testcontainers
@SpringBootTest(properties = {"booking-queue.admit-interval-ms=3600000", "booking-queue.admit-per-second=2"})
public class BookingQueueTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    @Autowired private BookingQueueService bookingQueueService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    @Test
    @DisplayName("오픈 직후에는 줄 선 순서대로 입장하고, 입장한 회원만 예매할 수 있다")
    void queueOrderAndGate() {
        Seat seat = seatWithOpenAt(LocalDateTime.now().minusMinutes(1));
        Schedule schedule = seat.getSchedule();
        Member a = TestFixtures.createAndSaveMember(memberRepository, "qA");
        Member b = TestFixtures.createAndSaveMember(memberRepository, "qB");
        Member c = TestFixtures.createAndSaveMember(memberRepository, "qC");
        LocalDateTime now = LocalDateTime.now();

        // 줄 서기: 선 순서대로 1, 2, 3번
        Assertions.assertEquals(1L, bookingQueueService.join(schedule, a.getId(), now).position());
        Assertions.assertEquals(2L, bookingQueueService.join(schedule, b.getId(), now).position());
        Assertions.assertEquals(3L, bookingQueueService.join(schedule, c.getId(), now).position());
        // 다시 서도 순번 유지
        Assertions.assertEquals(1L, bookingQueueService.join(schedule, a.getId(), now).position());

        // 입장 전에는 예매 불가 (좌석 락 전에 거절)
        BusinessException beforeAdmit = Assertions.assertThrows(BusinessException.class,
                () -> reservationService.reserve(a.getId(), seat.getId()));
        Assertions.assertEquals(ErrorCode.QUEUE_ENTRY_REQUIRED, beforeAdmit.getErrorCode());

        // 한 번에 2명 입장 -> A, B 입장, C는 맨 앞(1번)
        bookingQueueService.admitAll();
        Assertions.assertEquals("ADMITTED", bookingQueueService.status(schedule, a.getId(), now).state());
        Assertions.assertEquals("ADMITTED", bookingQueueService.status(schedule, b.getId(), now).state());
        QueueStatusDto cStatus = bookingQueueService.status(schedule, c.getId(), now);
        Assertions.assertEquals("WAITING", cStatus.state());
        Assertions.assertEquals(1L, cStatus.position());

        // 입장한 A는 예매 성공, 입장한 회원이 다시 줄을 서도 입장 상태 유지
        Assertions.assertNotNull(reservationService.reserve(a.getId(), seat.getId()));
        Assertions.assertEquals("ADMITTED", bookingQueueService.join(schedule, a.getId(), now).state());
    }

    @Test
    @DisplayName("입장 처리 중에 순번을 물어봐도 줄에서도 입장에서도 빠진 순간이 보이지 않는다")
    void admitWhilePolling_neverNotInQueue() throws Exception {
        Schedule schedule = seatWithOpenAt(LocalDateTime.now().minusMinutes(1)).getSchedule();
        LocalDateTime now = LocalDateTime.now();
        List<Long> memberIds = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            Long memberId = 900_000L + i;   // 대기열은 Redis만 보므로 회원을 DB에 만들 필요 없음
            bookingQueueService.join(schedule, memberId, now);
            memberIds.add(memberId);
        }

        // 한 스레드는 5명씩 입장시키고, 나머지는 그동안 모두의 순번을 계속 물어봄 (대기 화면 폴링)
        AtomicInteger notInQueue = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(9);
        Future<?> admitter = pool.submit(() -> {
            while (bookingQueueService.admitNext(schedule.getId(), 5) > 0) {
            }
        });
        List<Future<?>> pollers = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            pollers.add(pool.submit(() -> {
                while (!admitter.isDone()) {
                    for (Long memberId : memberIds) {
                        if ("NOT_IN_QUEUE".equals(bookingQueueService.status(schedule, memberId, now).state())) {
                            notInQueue.incrementAndGet();
                        }
                    }
                }
            }));
        }
        admitter.get(30, TimeUnit.SECONDS);
        for (Future<?> poller : pollers) {
            poller.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Assertions.assertEquals(0, notInQueue.get());
        for (Long memberId : memberIds) {
            Assertions.assertEquals("ADMITTED", bookingQueueService.status(schedule, memberId, now).state());
        }
    }

    @Test
    @DisplayName("대기열 운영 시간이 지난 회차는 줄 없이 바로 예매하고 Redis를 쓰지 않는다")
    void afterWindow_noQueue() {
        Seat seat = seatWithOpenAt(LocalDateTime.now().minusMinutes(31));
        Member member = TestFixtures.createAndSaveMember(memberRepository, "qLate");

        Assertions.assertEquals("NO_QUEUE",
                bookingQueueService.join(seat.getSchedule(), member.getId(), LocalDateTime.now()).state());
        Assertions.assertNotNull(reservationService.reserve(member.getId(), seat.getId()));
        Assertions.assertFalse(Boolean.TRUE.equals(redisTemplate.hasKey("queue:" + seat.getSchedule().getId() + ":waiting")));
    }

    @Test
    @DisplayName("예매 오픈 전에는 줄을 설 수 없고 오픈 시각을 알려준다")
    void beforeOpen_notOpen() {
        LocalDateTime openAt = LocalDateTime.now().plusMinutes(5).withNano(0);
        Seat seat = seatWithOpenAt(openAt);
        Member member = TestFixtures.createAndSaveMember(memberRepository, "qEarly");

        QueueStatusDto status = bookingQueueService.join(seat.getSchedule(), member.getId(), LocalDateTime.now());

        Assertions.assertEquals("NOT_OPEN", status.state());
        Assertions.assertEquals(openAt, status.bookingOpenAt());
    }

    private Seat seatWithOpenAt(LocalDateTime openAt) {
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Schedule schedule = seat.getSchedule();
        schedule.setBookingOpenAt(openAt);
        scheduleRepository.save(schedule);
        return seat;
    }
}
