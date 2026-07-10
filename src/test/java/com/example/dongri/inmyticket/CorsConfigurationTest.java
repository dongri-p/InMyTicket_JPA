package com.example.dongri.inmyticket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class CorsConfigurationTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("허용된 오리진에서의 preflight 요청은 Access-Control-Allow-Origin 헤더를 받는다")
    void preflight_fromAllowedOrigin_isAccepted() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://localhost:3000");
        headers.set("Access-Control-Request-Method", "GET");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/performances", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        Assertions.assertEquals("http://localhost:3000",
                response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    @Test
    @DisplayName("허용되지 않은 오리진에서의 preflight 요청은 Access-Control-Allow-Origin 헤더를 받지 못한다")
    void preflight_fromDisallowedOrigin_isRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Origin", "http://evil.example.com");
        headers.set("Access-Control-Request-Method", "GET");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/performances", HttpMethod.OPTIONS, new HttpEntity<>(headers), Void.class);

        Assertions.assertNull(response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }
}
