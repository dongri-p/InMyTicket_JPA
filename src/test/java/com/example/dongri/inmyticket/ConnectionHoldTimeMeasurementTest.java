package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.NaivePgApprovalSimulator;
import com.example.dongri.inmyticket.support.TestFixtures;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

/**
 * "외부 통신을 트랜잭션 안에서 하던 개선 전 설계" vs "외부 통신 → 내부 트랜잭션 → 반환으로
 * 분리한 현재 설계(PaymentService/PaymentApprovalService)"의 실제 DB 커넥션 점유 시간을
 * 로컬 H2 TCP 서버 + 실제 HikariPool을 대상으로 반복 실행해 비교 측정한다.
 *
 * 측정 기준: 트랜잭션 경계(approve() 호출) 자체의 벽시계 경과 시간.
 * 이 메서드 안에서는 DB 작업 외의 다른 I/O가 없고(PG 통신은 이미 트랜잭션 밖으로 분리되어 있음),
 * Hikari는 트랜잭션 시작 시 커넥션을 체크아웃해 커밋/롤백까지 그대로 들고 있으므로
 * "메서드 호출 경과 시간 ≈ 실제 커넥션 점유 시간"으로 봐도 무방하다.
 * (참고용으로 HikariPoolMXBean의 active-connection 카운트도 함께 폴링해 교차 확인한다 —
 * 다만 수 ms대의 매우 짧은 구간은 OS 스케줄러 해상도 때문에 폴링이 놓칠 수 있어 참고 지표로만 사용)
 */
@SpringBootTest
public class ConnectionHoldTimeMeasurementTest {

    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private NaivePgApprovalSimulator naivePgApprovalSimulator;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private DataSource dataSource;

    private String newPaymentKey() {
        return "conn-hold-test-key-" + UUID.randomUUID();
    }

    private Long newReservation(Member member) {
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        return reservationService.reserve(member.getId(), seat.getId());
    }

    static class ConnectionActiveTimeProbe implements Runnable {
        private final HikariPoolMXBean poolMXBean;
        private volatile boolean running = true;
        private volatile long activeNanos = 0;
        private volatile int maxObservedActive = 0;

        ConnectionActiveTimeProbe(HikariPoolMXBean poolMXBean) {
            this.poolMXBean = poolMXBean;
        }

        @Override
        public void run() {
            long last = System.nanoTime();
            while (running) {
                int active = poolMXBean.getActiveConnections();
                long now = System.nanoTime();
                if (active > 0) {
                    activeNanos += (now - last);
                }
                if (active > maxObservedActive) {
                    maxObservedActive = active;
                }
                last = now;
                LockSupport.parkNanos(20_000);
            }
        }

        void stop() {
            running = false;
        }

        double activeMillis() {
            return activeNanos / 1_000_000.0;
        }
    }

    // 트랜잭션 경계(approve 호출) 자체의 벽시계 경과시간을 잰다. 필요 시 참고용 active-connection
    // 폴링 프로브도 같이 붙인다(짧은 호출에서는 참고치일 뿐, 판정 근거는 항상 wall time).
    private long measureWallMillis(Runnable dbOperation, ConnectionActiveTimeProbe probe) {
        long start = System.nanoTime();
        dbOperation.run();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        return elapsedMs;
    }

    @Test
    @DisplayName("개선 전(트랜잭션 안에서 PG 통신) vs 개선 후(트랜잭션 밖에서 PG 통신) 커넥션 점유 시간 실측 비교 - 반복 측정")
    void compareConnectionHoldTime_beforeAndAfterRefactor() throws InterruptedException {
        HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
        HikariPoolMXBean poolMXBean = hikariDataSource.getHikariPoolMXBean();

        Member memberBefore = TestFixtures.createAndSaveMember(memberRepository, "connBefore");
        Member memberAfter = TestFixtures.createAndSaveMember(memberRepository, "connAfter");

        int beforeIterations = 3;
        int afterIterations = 10;

        // ===== 개선 전: PG 통신(1.5초)이 트랜잭션 "안에서" 실행되는 시나리오 =====
        List<Long> beforeTimes = new ArrayList<>();
        // 첫 반복에서만 active-connection 프로브를 같이 돌려, 실제로 그 구간 내내
        // 커넥션이 active 상태로 잡혀있는지 교차 확인한다.
        {
            Long reservationId = newReservation(memberBefore);
            ConnectionActiveTimeProbe probe = new ConnectionActiveTimeProbe(poolMXBean);
            Thread probeThread = new Thread(probe, "conn-active-probe-before");
            probeThread.start();
            Thread.sleep(20);

            long ms = measureWallMillis(() ->
                    naivePgApprovalSimulator.approveWithInlinePgCall(memberBefore.getId(), reservationId, newPaymentKey()), probe);

            Thread.sleep(20);
            probe.stop();
            probeThread.join();
            beforeTimes.add(ms);
            System.out.printf("  [개선 전 #1] wall=%d ms, active-connection 폴링 누적 시간=%.1f ms (교차확인용), max active=%d%n",
                    ms, probe.activeMillis(), probe.maxObservedActive);
        }
        for (int i = 1; i < beforeIterations; i++) {
            Long reservationId = newReservation(memberBefore);
            long ms = measureWallMillis(() ->
                    naivePgApprovalSimulator.approveWithInlinePgCall(memberBefore.getId(), reservationId, newPaymentKey()), null);
            beforeTimes.add(ms);
            System.out.printf("  [개선 전 #%d] wall=%d ms%n", i + 1, ms);
        }

        // ===== 개선 후: 실제 프로덕션 코드 PaymentApprovalService.approve() 만 측정 =====
        // 워밍업 1회(클래스 로딩/JIT 콜드스타트 영향 배제) 후 통계용 반복 측정
        {
            Long warmupReservationId = newReservation(memberAfter);
            paymentApprovalService.approve(memberAfter.getId(), warmupReservationId, newPaymentKey());
        }
        List<Long> afterTimes = new ArrayList<>();
        for (int i = 0; i < afterIterations; i++) {
            Long reservationId = newReservation(memberAfter);
            long ms = measureWallMillis(() ->
                    paymentApprovalService.approve(memberAfter.getId(), reservationId, newPaymentKey()), null);
            afterTimes.add(ms);
            System.out.printf("  [개선 후 #%d] wall=%d ms%n", i + 1, ms);
        }

        double beforeAvg = beforeTimes.stream().mapToLong(Long::longValue).average().orElseThrow();
        double afterAvg = afterTimes.stream().mapToLong(Long::longValue).average().orElseThrow();
        long afterMax = afterTimes.stream().mapToLong(Long::longValue).max().orElseThrow();
        double reductionPercent = (1 - (afterAvg / beforeAvg)) * 100;

        System.out.println("=== DB 커넥션 점유 시간 실측 결과 (로컬 H2 TCP + 실제 HikariPool) ===");
        System.out.printf("개선 전 평균 (트랜잭션 내부에서 PG 통신, n=%d): %.1f ms%n", beforeIterations, beforeAvg);
        System.out.printf("개선 후 평균 (트랜잭션 분리, 현재 코드, n=%d)  : %.1f ms (최대 %d ms)%n", afterIterations, afterAvg, afterMax);
        System.out.printf("감소율: %.2f%% (약 %.0f배 단축)%n", reductionPercent, beforeAvg / afterAvg);

        Assertions.assertTrue(beforeAvg >= 1400, "개선 전 시나리오는 평균 1.4초 이상 커넥션을 점유해야 한다");
        Assertions.assertTrue(afterMax < 500, "개선 후 시나리오는 매 반복 500ms 미만이어야 한다(트랜잭션에 PG 통신이 섞여 들어가지 않았음을 보증)");
        Assertions.assertTrue(reductionPercent > 90, "커넥션 점유 시간이 평균 90% 이상 감소해야 한다");
    }
}
