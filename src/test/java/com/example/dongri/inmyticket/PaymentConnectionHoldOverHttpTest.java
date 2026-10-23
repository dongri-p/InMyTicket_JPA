package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreatePaymentRequest;
import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

// ConnectionHoldTimeMeasurementTest는 서비스를 직접 호출해서 쟀기 때문에 OSIV의 영향을 받지 않음.
// 실제 HTTP 요청에서는 OSIV가 켜져 있으면 요청이 끝날 때까지 커넥션을 붙잡아, 트랜잭션을 나눠도
// PG 통신(데모 PG 1.5초) 내내 커넥션을 점유했음(open-in-view=true 실측 약 1,517ms, false 0~15ms).
// 결제 API를 실제로 호출하면서 커넥션 풀의 active 커넥션 수를 폴링해 점유 시간을 잰다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PaymentConnectionHoldOverHttpTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private DataSource dataSource;

    @Test
    @DisplayName("HTTP 결제 요청은 PG 통신 시간 동안 DB 커넥션을 붙잡고 있지 않는다")
    void paymentOverHttp_doesNotHoldConnectionDuringPgCall() throws InterruptedException {
        // given
        Member member = TestFixtures.createAndSaveMember(memberRepository, "connHoldUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        int amount = reservationRepository.findById(reservationId).orElseThrow().getTotalPrice();

        CreatePaymentRequest body = new CreatePaymentRequest();
        body.setReservationId(reservationId);
        body.setProvider(PgProvider.DEMO);
        body.setPaymentKey("conn-hold-key-" + UUID.randomUUID());
        body.setOrderId(PaymentService.orderIdPrefix(reservationId) + "connhold");
        body.setAmount(amount);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(member.getId(), member.getLoginId(), "USER"));
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 요청이 도는 동안 active 커넥션이 1개 이상인 시간을 누적
        HikariPoolMXBean pool = ((HikariDataSource) dataSource).getHikariPoolMXBean();
        AtomicBoolean running = new AtomicBoolean(true);
        long[] activeNanos = {0};
        Thread probe = new Thread(() -> {
            long last = System.nanoTime();
            while (running.get()) {
                long now = System.nanoTime();
                if (pool.getActiveConnections() > 0) {
                    activeNanos[0] += now - last;
                }
                last = now;
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        probe.start();

        // when
        long start = System.nanoTime();
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/payments", new HttpEntity<>(body, headers), String.class);
        long requestMs = (System.nanoTime() - start) / 1_000_000;
        running.set(false);
        probe.join();
        long activeMs = activeNanos[0] / 1_000_000;
        System.out.printf("HTTP 결제 요청 %dms 중 커넥션 점유 %dms%n", requestMs, activeMs);

        // then: 요청은 PG 통신만큼 걸리지만, 커넥션 점유는 그보다 훨씬 짧아야 함
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        Assertions.assertTrue(requestMs >= 1500, "데모 PG 지연(1.5초)이 요청에 포함돼야 함");
        Assertions.assertTrue(activeMs < 500, "PG 통신 동안 커넥션을 붙잡고 있음: " + activeMs + "ms");
    }
}
