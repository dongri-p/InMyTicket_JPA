package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentApprovalService;
import com.example.dongri.inmyticket.service.ReservationService;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.anyLong;

// 취소 API(HTTP) 한 요청 안에서 findPaymentToRefund()와 cancelAfterRefundCheck() 사이에 결제가 완료되는 경쟁을 재현.
// OSIV가 켜져 있으면 두 트랜잭션이 요청 단위 EntityManager를 공유해, 두 번째 락 조회가
// 첫 번째에서 캐시된 "결제 없음" 엔티티를 그대로 받아 환불 없이 취소해버림.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CancelPaymentRaceOverHttpTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PaymentApprovalService paymentApprovalService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @MockitoSpyBean private ReservationService reservationService;

    @Test
    @DisplayName("취소 요청 처리 도중 결제가 완료되면, 환불 없이 취소하지 않고 재시도를 요구한다")
    void cancelOverHttp_paymentCompletedMidRequest_doesNotCancelWithoutRefund() {
        // given: 결제 전(PENDING) 예약
        Member member = TestFixtures.createAndSaveMember(memberRepository, "osivRaceUser");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        Long reservationId = reservationService.reserve(member.getId(), seat.getId());

        // 환불 대상 확인(결제 없음) 직후, 다른 탭의 결제 요청이 먼저 커밋되는 상황을 끼워 넣음
        // (별도 스레드 = 별도 EntityManager로 실행해 실제 다른 요청과 같게 만듦)
        Mockito.doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            CompletableFuture.runAsync(() -> paymentApprovalService.approve(
                    member.getId(), reservationId, "osiv-race-key-" + UUID.randomUUID(), PgProvider.DEMO)).join();
            return result;
        }).when(reservationService).prepareRefund(anyLong(), anyLong());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(member.getId(), member.getLoginId(), "USER"));

        // when
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/reservations/" + reservationId + "/cancel", HttpMethod.POST, new HttpEntity<>(headers), String.class);

        // then: 결제가 생겼으므로 재시도를 요구해야 하고, 예약은 결제 완료 상태 그대로여야 함
        Assertions.assertFalse(response.getStatusCode().is2xxSuccessful(), "환불 없이 취소됨: " + response.getBody());
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow();
        Assertions.assertEquals(ReservationStatus.CONFIRMED, reservation.getStatus());
    }
}
