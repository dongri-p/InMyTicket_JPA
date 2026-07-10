package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.CreateReservationRequest;
import com.example.dongri.inmyticket.api.dto.LoginRequest;
import com.example.dongri.inmyticket.api.dto.LoginResponse;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.support.TestFixtures;

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

import java.util.Map;
import java.util.UUID;

// CreateReservationResponse/CreatePaymentResponse/CreateScheduleResponse를 CreateResourceResponse로
// 통합하면서 JSON 필드명이 reservationId/paymentId/scheduleId -> id로 바뀐 계약 변경을 실제 HTTP 응답으로 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateResourceResponseContractTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("예매 생성 응답의 리소스 id 필드명은 reservationId가 아니라 id이다")
    void reserveResponse_usesUnifiedIdField() {
        // given: 좌석 준비
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        // 회원가입 + 로그인
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CreateMemberRequest signupRequest = new CreateMemberRequest();
        signupRequest.setLoginId("contractUser" + suffix);
        signupRequest.setPassword("password123");
        signupRequest.setName("계약테스터");
        signupRequest.setEmail("contract" + suffix + "@test.com");
        restTemplate.postForEntity("/api/v1/members", signupRequest, Void.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLoginId("contractUser" + suffix);
        loginRequest.setPassword("password123");
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/api/v1/members/login", loginRequest, LoginResponse.class);
        String token = loginResponse.getBody().getAccessToken();

        // when: 예매 생성
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setSeatId(seat.getId());
        HttpEntity<CreateReservationRequest> request = new HttpEntity<>(reservationRequest, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity("/api/v1/reservations", request, Map.class);

        // then: 응답 JSON에 id 필드가 있고, 이전 필드명(reservationId)은 더 이상 없다
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().containsKey("id"));
        Assertions.assertNotNull(response.getBody().get("id"));
        Assertions.assertFalse(response.getBody().containsKey("reservationId"));
    }

    @Test
    @DisplayName("회원가입 응답도 다른 생성 API와 동일하게 id+message 필드를 가진다")
    void signupResponse_usesUnifiedIdField() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        CreateMemberRequest signupRequest = new CreateMemberRequest();
        signupRequest.setLoginId("signupCt" + suffix);
        signupRequest.setPassword("password123");
        signupRequest.setName("가입계약테스터");
        signupRequest.setEmail("signupCt" + suffix + "@test.com");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/members", signupRequest, Map.class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertNotNull(response.getBody().get("id"));
        Assertions.assertNotNull(response.getBody().get("message"));
    }
}
