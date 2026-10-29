package com.example.dongri.inmyticket;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.dongri.inmyticket.api.dto.ReservationListDto;
import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Payment;
import com.example.dongri.inmyticket.domain.PaymentStatus;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PaymentRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

// 결제한 예약을 공연 4일 전에 취소 - 미리 보기와 실제 취소가 같은 10% 수수료를 쓰고, 결제 기록·내 예매 응답에 남는지
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CancelWithFeeTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ReservationService reservationService;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("공연 4일 전 취소: 미리 보기와 실제 취소 모두 10% 수수료, 결제 기록에 수수료·환불액이 남는다")
    void cancelFourDaysBefore_tenPercentFee() {
        // given: 공연 4일 뒤, 이틀 전에 예매하고 결제한 예약 (24시간 무료 구간은 지남)
        Member member = TestFixtures.createAndSaveMember(memberRepository, "feeUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository, LocalDateTime.now().plusDays(4));
        // 내 예매 조회는 공연까지 조인하므로 회차에 공연을 붙임
        Performance performance = new Performance();
        performance.setApiId("fee-test-" + UUID.randomUUID().toString().substring(0, 8));
        performance.setTitle("수수료테스트공연");
        performanceRepository.save(performance);
        Schedule schedule = seat.getSchedule();
        schedule.setPerformance(performance);
        scheduleRepository.save(schedule);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        reservation.setReservedAt(LocalDateTime.now().minusDays(2));
        reservationRepository.save(reservation);
        paymentApprovalService.approve(member.getId(), reservationId, "fee-key-" + UUID.randomUUID(), PgProvider.DEMO);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(member.getId(), member.getLoginId(), "USER"));

        // when: 미리 보기 -> 취소
        ResponseEntity<Map> quote = restTemplate.exchange("/api/v1/reservations/" + reservationId + "/refund-quote",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        ResponseEntity<Void> cancel = restTemplate.exchange("/api/v1/reservations/" + reservationId + "/cancel",
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);

        // then
        Assertions.assertEquals(HttpStatus.OK, quote.getStatusCode());
        Assertions.assertEquals(10, quote.getBody().get("feeRate"));
        Assertions.assertEquals(15000, quote.getBody().get("cancelFee"));
        Assertions.assertEquals(135000, quote.getBody().get("refundAmount"));
        Assertions.assertEquals(HttpStatus.NO_CONTENT, cancel.getStatusCode());

        Payment payment = paymentRepository.findAll().stream()
                .filter(p -> p.getReservation().getId().equals(reservationId))
                .findFirst().orElseThrow();
        Assertions.assertEquals(PaymentStatus.CANCELED, payment.getStatus());
        Assertions.assertEquals(15000, payment.getCancelFee());
        Assertions.assertEquals(135000, payment.getRefundedAmount());

        ReservationListDto mine = reservationService.findMyReservation(member.getId(), reservationId);
        Assertions.assertEquals(15000, mine.getCancelFee());
        Assertions.assertEquals(135000, mine.getRefundedAmount());
    }

    @Test
    @DisplayName("이미 취소된 예약은 미리 보기도 409로 막는다 (PG 환불 통신 전에 거절)")
    void quoteAfterCancel_conflict() {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "feeUser2");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository, LocalDateTime.now().plusDays(10));
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(member.getId(), member.getLoginId(), "USER"));

        restTemplate.exchange("/api/v1/reservations/" + reservationId + "/cancel",
                HttpMethod.POST, new HttpEntity<>(headers), Void.class);
        ResponseEntity<Map> quote = restTemplate.exchange("/api/v1/reservations/" + reservationId + "/refund-quote",
                HttpMethod.GET, new HttpEntity<>(headers), Map.class);

        Assertions.assertEquals(HttpStatus.CONFLICT, quote.getStatusCode());
        Assertions.assertEquals("RESERVATION_ALREADY_CANCELLED", quote.getBody().get("code"));
    }
}
