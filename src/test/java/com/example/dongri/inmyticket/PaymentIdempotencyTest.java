package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreatePaymentRequest;
import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.external.DemoPgClient;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.ReservationRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PaymentService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// 결제 요청 멱등성: 같은 결제(paymentKey)를 다시 보내도 PG 승인은 한 번만, 응답은 처음과 같아야 함.
// 응답을 못 받은 클라이언트가 재시도하는 상황(타임아웃, 새로고침)을 재현
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PaymentIdempotencyTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private ReservationService reservationService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private ReservationRepository reservationRepository;
    @MockitoSpyBean private DemoPgClient demoPgClient;

    @Test
    @DisplayName("결제가 끝난 뒤 같은 요청을 다시 보내면 PG를 다시 부르지 않고 처음과 같은 201·결제 id를 돌려준다")
    void retryAfterSuccess_returnsSamePayment() {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "idemUser");
        Long reservationId = reserve(member);
        CreatePaymentRequest body = paymentRequest(reservationId, "idem-key-" + UUID.randomUUID());

        ResponseEntity<Map> first = pay(member, body);
        ResponseEntity<Map> retry = pay(member, body);

        Assertions.assertEquals(201, first.getStatusCode().value(), String.valueOf(first.getBody()));
        Assertions.assertEquals(201, retry.getStatusCode().value(), String.valueOf(retry.getBody()));
        Assertions.assertEquals(first.getBody().get("id"), retry.getBody().get("id"));
        Assertions.assertEquals(first.getHeaders().getLocation(), retry.getHeaders().getLocation());
        Mockito.verify(demoPgClient, Mockito.times(1)).approve(Mockito.eq(body.getPaymentKey()), Mockito.anyString(), Mockito.anyInt());
    }

    @Test
    @DisplayName("이미 쓰인 paymentKey로 다른 예약을 결제하려 하면 409 PAYMENT_KEY_REUSED")
    void sameKeyForOtherReservation_conflict() {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "idemReuse");
        String paymentKey = "idem-reuse-" + UUID.randomUUID();
        pay(member, paymentRequest(reserve(member), paymentKey));

        ResponseEntity<Map> reused = pay(member, paymentRequest(reserve(member), paymentKey));

        Assertions.assertEquals(409, reused.getStatusCode().value());
        Assertions.assertEquals("PAYMENT_KEY_REUSED", reused.getBody().get("code"));
    }

    @Test
    @DisplayName("첫 요청이 PG 통신 중일 때 같은 요청이 오면 409 PAYMENT_ALREADY_PROCESSING, 끝난 뒤 재시도하면 같은 결제를 돌려준다")
    void duplicateWhileProcessing_conflictThenReplay() throws Exception {
        Member member = TestFixtures.createAndSaveMember(memberRepository, "idemConcurrent");
        Long reservationId = reserve(member);
        CreatePaymentRequest body = paymentRequest(reservationId, "idem-concurrent-" + UUID.randomUUID());

        // 데모 PG는 1.5초 걸림 -> 그 사이에 같은 요청을 보냄
        CompletableFuture<ResponseEntity<Map>> first = CompletableFuture.supplyAsync(() -> pay(member, body));
        Thread.sleep(500);
        ResponseEntity<Map> duplicate = pay(member, body);
        ResponseEntity<Map> firstResult = first.get();
        ResponseEntity<Map> retry = pay(member, body);

        Assertions.assertEquals(409, duplicate.getStatusCode().value(), String.valueOf(duplicate.getBody()));
        Assertions.assertEquals("PAYMENT_ALREADY_PROCESSING", duplicate.getBody().get("code"));
        Assertions.assertEquals(201, firstResult.getStatusCode().value());
        Assertions.assertEquals(201, retry.getStatusCode().value());
        Assertions.assertEquals(firstResult.getBody().get("id"), retry.getBody().get("id"));
    }

    private Long reserve(Member member) {
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        return reservationService.reserve(member.getId(), seat.getId());
    }

    private CreatePaymentRequest paymentRequest(Long reservationId, String paymentKey) {
        CreatePaymentRequest body = new CreatePaymentRequest();
        body.setReservationId(reservationId);
        body.setProvider(PgProvider.DEMO);
        body.setPaymentKey(paymentKey);
        body.setOrderId(PaymentService.orderIdPrefix(reservationId) + "idem");
        body.setAmount(reservationRepository.findById(reservationId).orElseThrow().getTotalPrice());
        return body;
    }

    private ResponseEntity<Map> pay(Member member, CreatePaymentRequest body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(member.getId(), member.getLoginId(), "USER"));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/payments", new HttpEntity<>(body, headers), Map.class);
    }
}
