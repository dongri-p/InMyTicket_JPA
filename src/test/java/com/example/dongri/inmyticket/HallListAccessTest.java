package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.service.HallService;

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
import org.springframework.http.ResponseEntity;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class HallListAccessTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private HallService hallService;

    private ResponseEntity<String> getHalls(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtProvider.createToken(1L, "hallTester", role));
        return restTemplate.exchange("/api/v1/halls", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    @Test
    @DisplayName("관리자는 회차 등록에 쓸 공연장 목록을 조회할 수 있다")
    void admin_canListHalls() {
        String name = "목록테스트홀" + UUID.randomUUID().toString().substring(0, 8);
        hallService.saveHall(name, "서울", 20);

        ResponseEntity<String> response = getHalls("ADMIN");

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().contains(name));
        Assertions.assertTrue(response.getBody().contains("\"totalSeats\":20"));
    }

    @Test
    @DisplayName("일반 회원은 공연장 목록을 조회할 수 없다 (403)")
    void user_cannotListHalls() {
        ResponseEntity<String> response = getHalls("USER");

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
