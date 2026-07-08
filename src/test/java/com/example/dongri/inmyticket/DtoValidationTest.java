package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DtoValidationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("회원가입 시 과도하게 긴 loginId는 400으로 거부된다")
    void signup_withOverlongLoginId_returns400() {
        CreateMemberRequest request = new CreateMemberRequest();
        request.setLoginId("a".repeat(1000));
        request.setPassword("password123");
        request.setName("길이테스터");
        request.setEmail("lengthtest@test.com");

        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/members", request, String.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
