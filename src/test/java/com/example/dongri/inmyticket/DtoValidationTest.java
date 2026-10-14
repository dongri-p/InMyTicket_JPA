package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

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

    @ParameterizedTest
    @ValueSource(strings = {"ab cd", " abcd", "abcd ", "아이디테스트", "abc!d"})
    @DisplayName("회원가입 시 공백·한글·특수문자가 섞인 loginId는 400으로 거부된다")
    void signup_withInvalidCharactersInLoginId_returns400(String loginId) {
        CreateMemberRequest request = new CreateMemberRequest();
        request.setLoginId(loginId);
        request.setPassword("password123");
        request.setName("패턴테스터");
        request.setEmail("patterntest@test.com");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/members", request, Map.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("아이디는 영문과 숫자만 사용할 수 있습니다.", response.getBody().get("message"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab cd", " abcd", "abcd "})
    @DisplayName("로그인 시 공백이 들어간 loginId는 400으로 거부된다 (규칙 이전에 가입한 공백 아이디도 로그인 불가)")
    void login_withWhitespaceInLoginId_returns400(String loginId) {
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/v1/members/login",
                Map.of("loginId", loginId, "password", "password123"), Map.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals("아이디에 공백을 넣을 수 없습니다.", response.getBody().get("message"));
    }
}
