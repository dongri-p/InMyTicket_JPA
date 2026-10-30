package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateHallRequest;
import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.CreatePaymentRequest;
import com.example.dongri.inmyticket.api.dto.CreateReservationRequest;
import com.example.dongri.inmyticket.api.dto.CreateScheduleRequest;
import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.PgProvider;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;
import com.example.dongri.inmyticket.service.ScheduleService;
import com.example.dongri.inmyticket.support.TestFixtures;
import org.springframework.data.domain.PageRequest;

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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 생성 API가 내려준 Location을 그대로 GET 했을 때 방금 만든 리소스가 조회되는지 실제 HTTP로 확인
// (전에는 Location만 있고 단건 조회 API가 없어서 그 주소로 가면 404였음)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LocationLookupTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private ScheduleService scheduleService;
    @Autowired private SeatRepository seatRepository;

    @Test
    @DisplayName("예매 Location으로 본인 예약을 조회할 수 있고, 다른 회원은 403, 없는 예약은 404다")
    void reservationLocation() {
        Member owner = TestFixtures.createAndSaveMember(memberRepository, "locOwner");
        Member other = TestFixtures.createAndSaveMember(memberRepository, "locOther");
        ResponseEntity<Map> created = post("/api/v1/reservations", reservationRequest(), userToken(owner));
        URI location = created.getHeaders().getLocation();

        ResponseEntity<Map> mine = get(location, userToken(owner));
        ResponseEntity<Map> others = get(location, userToken(other));
        ResponseEntity<Map> missing = get(URI.create("/api/v1/reservations/999999"), userToken(owner));

        Assertions.assertEquals(HttpStatus.OK, mine.getStatusCode());
        Assertions.assertEquals(created.getBody().get("id"), mine.getBody().get("reservationId"));
        Assertions.assertEquals("PENDING", mine.getBody().get("status"));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, others.getStatusCode());
        Assertions.assertEquals("NOT_RESERVATION_OWNER", others.getBody().get("code"));
        Assertions.assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        Assertions.assertEquals("RESERVATION_NOT_FOUND", missing.getBody().get("code"));
    }

    @Test
    @DisplayName("결제 Location으로 본인 결제를 조회할 수 있고, 다른 회원은 403, 없는 결제는 404다")
    void paymentLocation() {
        Member owner = TestFixtures.createAndSaveMember(memberRepository, "payLocOwner");
        Member other = TestFixtures.createAndSaveMember(memberRepository, "payLocOther");
        Long reservationId = ((Number) post("/api/v1/reservations", reservationRequest(), userToken(owner))
                .getBody().get("id")).longValue();
        int amount = ((Number) get(URI.create("/api/v1/reservations/" + reservationId), userToken(owner))
                .getBody().get("totalPrice")).intValue();

        CreatePaymentRequest payment = new CreatePaymentRequest();
        payment.setReservationId(reservationId);
        payment.setProvider(PgProvider.DEMO);
        payment.setPaymentKey("loc-key-" + UUID.randomUUID());
        payment.setOrderId("imt-" + reservationId + "-loc");
        payment.setAmount(amount);
        ResponseEntity<Map> created = post("/api/v1/payments", payment, userToken(owner));
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode());
        URI location = created.getHeaders().getLocation();

        ResponseEntity<Map> mine = get(location, userToken(owner));
        ResponseEntity<Map> others = get(location, userToken(other));
        ResponseEntity<Map> missing = get(URI.create("/api/v1/payments/999999"), userToken(owner));

        Assertions.assertEquals(HttpStatus.OK, mine.getStatusCode());
        Assertions.assertEquals(created.getBody().get("id"), mine.getBody().get("paymentId"));
        Assertions.assertEquals(reservationId, ((Number) mine.getBody().get("reservationId")).longValue());
        Assertions.assertEquals(amount, mine.getBody().get("amount"));
        Assertions.assertEquals("COMPLETED", mine.getBody().get("status"));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, others.getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
        Assertions.assertEquals("PAYMENT_NOT_FOUND", missing.getBody().get("code"));
    }

    @Test
    @DisplayName("회원가입 Location으로 본인 정보를 조회할 수 있고(비밀번호 제외), 다른 회원 정보는 403이다")
    void memberLocation() {
        String loginId = "locMember" + UUID.randomUUID().toString().substring(0, 8);
        CreateMemberRequest signup = new CreateMemberRequest();
        signup.setLoginId(loginId);
        signup.setPassword("password123");
        signup.setName("위치테스터");
        signup.setEmail(loginId + "@test.com");
        ResponseEntity<Map> created = restTemplate.postForEntity("/api/v1/members", signup, Map.class);
        Long memberId = ((Number) created.getBody().get("id")).longValue();
        Member other = TestFixtures.createAndSaveMember(memberRepository, "locMemberOther");

        ResponseEntity<Map> mine = get(created.getHeaders().getLocation(),
                jwtProvider.createToken(memberId, loginId, "USER"));
        ResponseEntity<Map> others = get(created.getHeaders().getLocation(), userToken(other));

        Assertions.assertEquals(HttpStatus.OK, mine.getStatusCode());
        Assertions.assertEquals(loginId, mine.getBody().get("loginId"));
        Assertions.assertFalse(mine.getBody().containsKey("password"));
        Assertions.assertEquals(HttpStatus.FORBIDDEN, others.getStatusCode());
    }

    @Test
    @DisplayName("공연장·회차 Location도 조회된다 (공연장은 관리자만, 회차는 누구나)")
    void hallAndScheduleLocation() {
        String adminToken = jwtProvider.createToken(1L, "locAdmin", "ADMIN");
        Long performanceId = savePerformance().getId();

        CreateHallRequest hall = new CreateHallRequest();
        hall.setName("위치테스트홀" + UUID.randomUUID().toString().substring(0, 8));
        hall.setAddress("서울");
        hall.setTotalSeats(10);
        ResponseEntity<Map> createdHall = post("/api/v1/halls", hall, adminToken);
        Long hallId = ((Number) createdHall.getBody().get("id")).longValue();

        CreateScheduleRequest schedule = new CreateScheduleRequest();
        schedule.setPerformanceId(performanceId);
        schedule.setHallId(hallId);
        schedule.setStartTime(LocalDateTime.now().plusDays(30).withNano(0));
        schedule.setTotalSeatCount(10);
        ResponseEntity<Map> createdSchedule = post("/api/v1/schedules", schedule, adminToken);
        Assertions.assertEquals(HttpStatus.CREATED, createdSchedule.getStatusCode(), String.valueOf(createdSchedule.getBody()));

        ResponseEntity<Map> hallByAdmin = get(createdHall.getHeaders().getLocation(), adminToken);
        ResponseEntity<Map> hallByUser = get(createdHall.getHeaders().getLocation(),
                jwtProvider.createToken(2L, "locUser", "USER"));
        ResponseEntity<Map> scheduleAnonymous = restTemplate.getForEntity(
                createdSchedule.getHeaders().getLocation(), Map.class);

        Assertions.assertEquals(HttpStatus.OK, hallByAdmin.getStatusCode());
        Assertions.assertEquals(hallId, ((Number) hallByAdmin.getBody().get("hallId")).longValue());
        Assertions.assertEquals(HttpStatus.FORBIDDEN, hallByUser.getStatusCode());
        Assertions.assertEquals(HttpStatus.OK, scheduleAnonymous.getStatusCode());
        Assertions.assertEquals(createdSchedule.getBody().get("id"), scheduleAnonymous.getBody().get("scheduleId"));
        Assertions.assertEquals(10, scheduleAnonymous.getBody().get("availableSeatCount"));
        Assertions.assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/v1/schedules/999999", Map.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND, get(URI.create("/api/v1/halls/999999"), adminToken).getStatusCode());
    }

    // 예매 조회 응답에 공연명이 들어가므로 공연·공연장에 연결된 실제 회차의 좌석으로 예매
    private CreateReservationRequest reservationRequest() {
        Hall hall = new Hall();
        hall.setName("위치테스트홀" + UUID.randomUUID().toString().substring(0, 8));
        hall.setTotalSeats(10);
        hallRepository.save(hall);
        Long scheduleId = scheduleService.saveSchedule(
                savePerformance().getId(), hall.getId(), LocalDateTime.now().plusDays(1), 1);
        Seat seat = seatRepository.findByScheduleIdOrderBySeatNumberAscIdAsc(scheduleId, PageRequest.of(0, 1)).getContent().get(0);

        CreateReservationRequest request = new CreateReservationRequest();
        request.setSeatIds(List.of(seat.getId()));
        return request;
    }

    private Performance savePerformance() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Performance performance = new Performance();
        performance.setApiId("location-test-" + suffix);
        performance.setTitle("위치테스트공연" + suffix);
        return performanceRepository.save(performance);
    }

    private String userToken(Member member) {
        return jwtProvider.createToken(member.getId(), member.getLoginId(), "USER");
    }

    private ResponseEntity<Map> post(String url, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(url, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> get(URI location, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(location, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }
}
