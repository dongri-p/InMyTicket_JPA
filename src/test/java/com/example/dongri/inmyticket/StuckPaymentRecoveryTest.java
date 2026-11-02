package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.external.DemoPgClient;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.LocalDateTime;
import java.util.UUID;

// 승인 응답을 못 받았을 때 돈과 예약이 어긋나지 않는지: 결과를 모르면 PROCESSING으로 두고, PG에 물어서 확정하거나 되돌림.
// 토스 대신 DemoPgClient를 spy로 바꿔 "PG가 승인했다/안 했다/응답이 없다"를 재현
@SpringBootTest
public class StuckPaymentRecoveryTest {

    @Autowired private PaymentService paymentService;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @MockitoSpyBean private DemoPgClient demoPgClient;

    private Member member;
    private Seat seat;
    private Long reservationId;
    private String paymentKey;

    @BeforeEach
    void setUp() {
        member = TestFixtures.createAndSaveMember(memberRepository, "stuckPay");
        seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        reservationId = reservationService.reserve(member.getId(), seat.getId());
        paymentKey = "stuck-key-" + UUID.randomUUID();
    }

    private int price() {
        return reservationRepository.findById(reservationId).orElseThrow().getTotalPrice();
    }

    private ReservationStatus status() {
        return reservationRepository.findById(reservationId).orElseThrow().getStatus();
    }

    private void pay() {
        paymentService.processPayment(member.getId(), reservationId, PgProvider.DEMO, paymentKey,
                PaymentService.orderIdPrefix(reservationId) + "abc", price());
    }

    // 승인 응답을 못 받고 10분이 지난 예약
    private void leaveStuck() {
        reservationService.beginPaymentProcessing(member.getId(), reservationId, price(), paymentKey, PgProvider.DEMO);
        Reservation stuck = reservationRepository.findById(reservationId).orElseThrow();
        stuck.setProcessingStartedAt(LocalDateTime.now().minusMinutes(10));
        reservationRepository.save(stuck);
    }

    private int recover() {
        return paymentService.recoverStuckPayments(LocalDateTime.now().minusMinutes(5));
    }

    @Test
    @DisplayName("승인 응답을 못 받으면 PENDING으로 되돌리지 않고 PROCESSING으로 둔다")
    void approveTimeout_staysProcessing() {
        Mockito.doThrow(new BusinessException(ErrorCode.PG_UNAVAILABLE))
                .when(demoPgClient).approve(Mockito.eq(paymentKey), Mockito.anyString(), Mockito.anyInt());

        BusinessException thrown = Assertions.assertThrows(BusinessException.class, this::pay);

        Assertions.assertEquals(ErrorCode.PAYMENT_RESULT_UNKNOWN, thrown.getErrorCode());
        Assertions.assertEquals(ReservationStatus.PROCESSING, status());
    }

    @Test
    @DisplayName("PG가 거절하면 승인된 돈이 없으므로 바로 PENDING으로 되돌린다")
    void approveRejected_revertsToPending() {
        Mockito.doThrow(new BusinessException(ErrorCode.PAYMENT_REJECTED))
                .when(demoPgClient).approve(Mockito.eq(paymentKey), Mockito.anyString(), Mockito.anyInt());

        BusinessException thrown = Assertions.assertThrows(BusinessException.class, this::pay);

        Assertions.assertEquals(ErrorCode.PAYMENT_REJECTED, thrown.getErrorCode());
        Assertions.assertEquals(ReservationStatus.PENDING, status());
    }

    @Test
    @DisplayName("PG에서 승인된 결제면 다시 승인하지 않고 결제를 기록해 예매를 확정한다")
    void stuck_approvedAtPg_confirms() {
        leaveStuck();
        Mockito.doReturn(true).when(demoPgClient).isApproved(paymentKey);

        Assertions.assertEquals(1, recover());

        Assertions.assertEquals(ReservationStatus.CONFIRMED, status());
        Assertions.assertTrue(paymentRepository.existsByPaymentKey(paymentKey));
        Mockito.verify(demoPgClient, Mockito.never()).approve(Mockito.any(), Mockito.any(), Mockito.anyInt());
        Mockito.verify(demoPgClient, Mockito.never()).cancel(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("PG에서 승인되지 않은 결제면 PENDING으로 되돌리고 저장한 결제 키를 지운다")
    void stuck_notApprovedAtPg_revertsToPending() {
        leaveStuck();
        Mockito.doReturn(false).when(demoPgClient).isApproved(paymentKey);

        Assertions.assertEquals(1, recover());

        Reservation reverted = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.PENDING, reverted.getStatus());
        Assertions.assertNull(reverted.getProcessingPaymentKey());
        Assertions.assertFalse(paymentRepository.existsByPaymentKey(paymentKey));
    }

    @Test
    @DisplayName("PG 조회가 실패하면 결론 내지 않고 PROCESSING으로 두었다가 다음 주기에 다시 묻는다")
    void stuck_lookupFails_retriesNextTime() {
        leaveStuck();
        Mockito.doThrow(new BusinessException(ErrorCode.PG_UNAVAILABLE)).when(demoPgClient).isApproved(paymentKey);

        Assertions.assertEquals(0, recover());
        Assertions.assertEquals(ReservationStatus.PROCESSING, status());

        Mockito.doReturn(true).when(demoPgClient).isApproved(paymentKey);
        Assertions.assertEquals(1, recover());
        Assertions.assertEquals(ReservationStatus.CONFIRMED, status());
    }

    @Test
    @DisplayName("승인됐지만 그사이 공연이 시작돼 확정할 수 없으면 PG 승인을 취소하고 되돌린다")
    void stuck_approvedButShowStarted_cancelsAtPg() {
        leaveStuck();
        Schedule schedule = scheduleRepository.findById(seat.getSchedule().getId()).orElseThrow();
        schedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(schedule);
        Mockito.doReturn(true).when(demoPgClient).isApproved(paymentKey);

        recover();

        Mockito.verify(demoPgClient).cancel(Mockito.eq(paymentKey), Mockito.anyString(), Mockito.isNull());
        Assertions.assertEquals(ReservationStatus.PENDING, status());
        Assertions.assertFalse(paymentRepository.existsByPaymentKey(paymentKey));
    }

    @Test
    @DisplayName("승인 취소까지 실패하면 PROCESSING으로 두었다가 다음 주기에 다시 취소한다")
    void stuck_cancelFails_retriesNextTime() {
        leaveStuck();
        Schedule schedule = scheduleRepository.findById(seat.getSchedule().getId()).orElseThrow();
        schedule.setStartTime(LocalDateTime.now().minusMinutes(1));
        scheduleRepository.save(schedule);
        Mockito.doReturn(true).when(demoPgClient).isApproved(paymentKey);
        Mockito.doThrow(new BusinessException(ErrorCode.PG_UNAVAILABLE)).doNothing()
                .when(demoPgClient).cancel(Mockito.eq(paymentKey), Mockito.anyString(), Mockito.isNull());

        recover();
        Assertions.assertEquals(ReservationStatus.PROCESSING, status());

        recover();
        Assertions.assertEquals(ReservationStatus.PENDING, status());
        Mockito.verify(demoPgClient, Mockito.times(2)).cancel(Mockito.eq(paymentKey), Mockito.anyString(), Mockito.isNull());
    }

    @Test
    @DisplayName("방금 결제를 시작한 예약은 PG에 묻지 않는다")
    void inFlight_isNotLookedUp() {
        reservationService.beginPaymentProcessing(member.getId(), reservationId, price(), paymentKey, PgProvider.DEMO);

        Assertions.assertEquals(0, recover());

        Mockito.verify(demoPgClient, Mockito.never()).isApproved(paymentKey);
        Assertions.assertEquals(ReservationStatus.PROCESSING, status());
    }
}
