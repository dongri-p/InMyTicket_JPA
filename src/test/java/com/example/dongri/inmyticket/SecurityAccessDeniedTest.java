package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.LoginRequest;
import com.example.dongri.inmyticket.api.dto.LoginResponse;

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

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SecurityAccessDeniedTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("일반 회원이 관리자 전용 API를 호출하면 커스텀 JSON 형식의 403 응답을 받는다")
    void nonAdminAccessingAdminApi_returnsCustom403() {
        // given: 일반 회원 가입 후 로그인해서 JWT 발급받기
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        CreateMemberRequest signupRequest = new CreateMemberRequest();
        signupRequest.setLoginId("accessUser" + suffix);
        signupRequest.setPassword("password123");
        signupRequest.setName("권한테스터");
        signupRequest.setEmail("access" + suffix + "@test.com");
        restTemplate.postForEntity("/api/v1/members", signupRequest, Void.class);

        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setLoginId("accessUser" + suffix);
        loginRequest.setPassword("password123");
        ResponseEntity<LoginResponse> loginResponse =
                restTemplate.postForEntity("/api/v1/members/login", loginRequest, LoginResponse.class);
        String token = loginResponse.getBody().getAccessToken();

        // when: 일반 회원 권한으로 관리자 전용 API(회차 등록) 호출
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>("{}", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/schedules", request, String.class);

        // then: Whitelabel 에러 페이지가 아닌 커스텀 JSON 403 응답이어야 한다
        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Assertions.assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        Assertions.assertTrue(response.getBody().contains("\"status\":403"));
    }
}
