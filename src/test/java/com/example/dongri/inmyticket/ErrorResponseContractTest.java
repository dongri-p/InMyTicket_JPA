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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 에러 응답은 {status, code, message}이고, 화면이 분기할 code가 상황별로 다르게 온다는 것을 실제 HTTP로 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ErrorResponseContractTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("이미 예매된 좌석은 409 SEAT_ALREADY_RESERVED, 없는 좌석은 400 SEAT_NOT_FOUND이고 내부 id는 메시지에 나오지 않는다")
    void reserveErrors_haveDistinctCodes() {
        String first = signupAndLogin("errFirst");
        String second = signupAndLogin("errSecond");
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);
        reserve(first, seat.getId());

        Map<String, Object> taken = reserve(second, seat.getId()).getBody();
        Map<String, Object> missing = reserve(second, 999999L).getBody();

        assertError(taken, 409, "SEAT_ALREADY_RESERVED");
        assertError(missing, 400, "SEAT_NOT_FOUND");
        Assertions.assertFalse(((String) missing.get("message")).contains("999999"), "내부 id 노출: " + missing);
    }

    @Test
    @DisplayName("인증 없음 401 UNAUTHORIZED, 입력 검증 실패 400 INVALID_REQUEST, 없는 예약 404 RESERVATION_NOT_FOUND, 없는 경로 404 PATH_NOT_FOUND")
    void commonErrors_haveCodes() {
        String token = signupAndLogin("errCommon");

        Map<String, Object> noAuth = restTemplate.postForEntity("/api/v1/reservations",
                json(null, new CreateReservationRequest()), Map.class).getBody();
        Map<String, Object> invalid = restTemplate.postForEntity("/api/v1/reservations",
                json(token, new CreateReservationRequest()), Map.class).getBody();
        Map<String, Object> noReservation = restTemplate.postForEntity("/api/v1/reservations/999999/cancel",
                json(token, null), Map.class).getBody();
        // 없는 경로도 인증 없이 부르면 401이 먼저 (경로 존재 여부를 비회원에게 알려주지 않음)
        Map<String, Object> noPath = restTemplate.exchange("/api/v1/nothing-here", org.springframework.http.HttpMethod.GET,
                json(token, null), Map.class).getBody();

        assertError(noAuth, 401, "UNAUTHORIZED");
        assertError(invalid, 400, "INVALID_REQUEST");
        Assertions.assertEquals("좌석을 선택해주세요.", invalid.get("message"));
        assertError(noReservation, 404, "RESERVATION_NOT_FOUND");
        assertError(noPath, 404, "PATH_NOT_FOUND");
    }

    private void assertError(Map<String, Object> body, int status, String code) {
        Assertions.assertEquals(status, ((Number) body.get("status")).intValue(), body.toString());
        Assertions.assertEquals(code, body.get("code"), body.toString());
        Assertions.assertNotNull(body.get("message"));
    }

    private ResponseEntity<Map> reserve(String token, Long seatId) {
        CreateReservationRequest request = new CreateReservationRequest();
        request.setSeatIds(List.of(seatId));
        return restTemplate.postForEntity("/api/v1/reservations", json(token, request), Map.class);
    }

    private <T> HttpEntity<T> json(String token, T body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String signupAndLogin(String prefix) {
        String loginId = prefix + UUID.randomUUID().toString().substring(0, 8);
        CreateMemberRequest signup = new CreateMemberRequest();
        signup.setLoginId(loginId);
        signup.setPassword("password123");
        signup.setName("에러테스터");
        signup.setEmail(loginId + "@test.com");
        restTemplate.postForEntity("/api/v1/members", signup, Void.class);

        LoginRequest login = new LoginRequest();
        login.setLoginId(loginId);
        login.setPassword("password123");
        return restTemplate.postForEntity("/api/v1/members/login", login, LoginResponse.class).getBody().getAccessToken();
    }
}
