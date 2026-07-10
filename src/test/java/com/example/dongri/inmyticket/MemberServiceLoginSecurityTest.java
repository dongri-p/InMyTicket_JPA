package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.LoginRequest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class MemberServiceLoginSecurityTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인해도 존재하는 아이디의 잘못된 비밀번호와 동일한 400 응답을 받는다")
    void login_withNonExistentLoginId_returnsSameErrorAsWrongPassword() {
        // when: 존재하지 않는 아이디로 로그인 시도
        LoginRequest request = new LoginRequest();
        request.setLoginId("no-such-user-" + UUID.randomUUID());
        request.setPassword("whatever123");

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/members/login", request, String.class);

        // then: 계정 존재 여부가 드러나지 않는 동일한 메시지의 400 응답
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains("아이디 또는 비밀번호가 일치하지 않습니다"));
    }

    @Test
    @DisplayName("같은 아이디로 로그인을 5회 연속 실패하면, 이후 올바른 비밀번호로도 잠금 응답을 받는다")
    void login_afterTooManyFailures_locksOutEvenWithCorrectPassword() {
        // given: 회원가입
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "lockoutUser" + suffix;
        String correctPassword = "password123";

        CreateMemberRequest signupRequest = new CreateMemberRequest();
        signupRequest.setLoginId(loginId);
        signupRequest.setPassword(correctPassword);
        signupRequest.setName("잠금테스터");
        signupRequest.setEmail("lockout" + suffix + "@test.com");
        restTemplate.postForEntity("/api/v1/members", signupRequest, Void.class);

        LoginRequest wrongPasswordRequest = new LoginRequest();
        wrongPasswordRequest.setLoginId(loginId);
        wrongPasswordRequest.setPassword("wrong-password");

        // when: 잘못된 비밀번호로 5회 연속 실패
        for (int i = 0; i < 5; i++) {
            restTemplate.postForEntity("/api/v1/members/login", wrongPasswordRequest, String.class);
        }

        // then: 올바른 비밀번호로 시도해도 잠금(409) 응답을 받는다
        LoginRequest correctPasswordRequest = new LoginRequest();
        correctPasswordRequest.setLoginId(loginId);
        correctPasswordRequest.setPassword(correctPassword);

        ResponseEntity<String> lockedResponse =
                restTemplate.postForEntity("/api/v1/members/login", correctPasswordRequest, String.class);

        Assertions.assertEquals(HttpStatus.CONFLICT, lockedResponse.getStatusCode());
        Assertions.assertTrue(lockedResponse.getBody().contains("로그인 시도가 너무 많습니다"));
    }
}
