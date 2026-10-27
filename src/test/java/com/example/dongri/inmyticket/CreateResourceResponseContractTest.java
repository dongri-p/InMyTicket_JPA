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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 생성 API는 201 Created + Location + {id}, 본문 없는 동작(취소)은 204 No Content라는 응답 규칙을 실제 HTTP로 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CreateResourceResponseContractTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("예매 생성은 201과 Location 헤더, 본문엔 id만 내려준다")
    void reserveResponse_created() {
        // when
        ResponseEntity<Map> response = reserve(signupAndLogin("contractUser"));

        // then
        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Object id = response.getBody().get("id");
        Assertions.assertNotNull(id);
        Assertions.assertEquals(List.of("id"), List.copyOf(response.getBody().keySet()));
        Assertions.assertTrue(response.getHeaders().getLocation().getPath().endsWith("/api/v1/reservations/" + id));
    }

    @Test
    @DisplayName("회원가입도 다른 생성 API와 같이 201 + Location + id다")
    void signupResponse_created() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/members", signupRequest("signupCt" + suffix), Map.class);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Object id = response.getBody().get("id");
        Assertions.assertEquals(List.of("id"), List.copyOf(response.getBody().keySet()));
        Assertions.assertTrue(response.getHeaders().getLocation().getPath().endsWith("/api/v1/members/" + id));
    }

    @Test
    @DisplayName("예매 취소는 POST /cancel로 204를 받고, 이미 취소된 예약을 다시 취소하면 409다")
    void cancel_noContent_thenConflict() {
        String token = signupAndLogin("cancelCt");
        Object id = reserve(token).getBody().get("id");

        ResponseEntity<String> first = cancel(token, id);
        ResponseEntity<String> second = cancel(token, id);

        Assertions.assertEquals(HttpStatus.NO_CONTENT, first.getStatusCode());
        Assertions.assertNull(first.getBody());
        Assertions.assertEquals(HttpStatus.CONFLICT, second.getStatusCode());
    }

    @Test
    @DisplayName("예전 DELETE 취소 URL은 404, 경로는 있는데 메서드가 다르면 405다 (둘 다 500이 아님)")
    void oldDeleteCancel_notFound_wrongMethod_methodNotAllowed() {
        String token = signupAndLogin("deleteCt");
        Object id = reserve(token).getBody().get("id");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        ResponseEntity<String> oldDelete = restTemplate.exchange("/api/v1/reservations/{id}", HttpMethod.DELETE,
                new HttpEntity<>(headers), String.class, id);
        ResponseEntity<String> wrongMethod = restTemplate.exchange("/api/v1/reservations", HttpMethod.PUT,
                new HttpEntity<>(headers), String.class);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, oldDelete.getStatusCode());
        Assertions.assertEquals(HttpStatus.METHOD_NOT_ALLOWED, wrongMethod.getStatusCode());
    }

    @Test
    @DisplayName("URL이 가리키는 공연·회차·예약이 없으면 404, 본문에 담긴 id가 없으면 400이다")
    void missingResource_notFound_missingBodyReference_badRequest() {
        String token = signupAndLogin("notFoundCt");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Assertions.assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/v1/performances/999999", String.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/v1/performances/999999/schedules", String.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND,
                restTemplate.getForEntity("/api/v1/schedules/999999/seats", String.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.NOT_FOUND, cancel(token, 999999L).getStatusCode());

        // 본문 안의 좌석 id가 없는 경우는 요청 내용 오류
        CreateReservationRequest request = new CreateReservationRequest();
        request.setSeatIds(List.of(999999L));
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, restTemplate.postForEntity(
                "/api/v1/reservations", new HttpEntity<>(request, headers), String.class).getStatusCode());
    }

    private ResponseEntity<String> cancel(String token, Object reservationId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange("/api/v1/reservations/{id}/cancel", HttpMethod.POST,
                new HttpEntity<>(headers), String.class, reservationId);
    }

    private ResponseEntity<Map> reserve(String token) {
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        CreateReservationRequest reservationRequest = new CreateReservationRequest();
        reservationRequest.setSeatIds(List.of(seat.getId()));
        return restTemplate.postForEntity("/api/v1/reservations", new HttpEntity<>(reservationRequest, headers), Map.class);
    }

    private String signupAndLogin(String prefix) {
        String loginId = prefix + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.postForEntity("/api/v1/members", signupRequest(loginId), Void.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLoginId(loginId);
        loginRequest.setPassword("password123");
        return restTemplate.postForEntity("/api/v1/members/login", loginRequest, LoginResponse.class)
                .getBody().getAccessToken();
    }

    private CreateMemberRequest signupRequest(String loginId) {
        CreateMemberRequest request = new CreateMemberRequest();
        request.setLoginId(loginId);
        request.setPassword("password123");
        request.setName("계약테스터");
        request.setEmail(loginId + "@test.com");
        return request;
    }
}
