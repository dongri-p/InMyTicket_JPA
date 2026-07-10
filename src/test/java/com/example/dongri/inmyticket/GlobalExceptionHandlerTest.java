package com.example.dongri.inmyticket;

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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GlobalExceptionHandlerTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("예상치 못한 예외도 표준 에러 JSON 포맷(500)으로 응답해야 한다")
    void unexpectedException_returnsStandardErrorFormat() {
        // 회원가입 API에 파싱 자체가 불가능한 JSON 본문을 보내면, 어떤 핸들러도 다루지 않는
        // HttpMessageNotReadableException이 발생해 포괄 Exception 핸들러(500)로 떨어진다
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> malformedJsonRequest = new HttpEntity<>("{ invalid json", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/members", malformedJsonRequest, String.class);

        Assertions.assertEquals(500, response.getStatusCode().value());
        Assertions.assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        Assertions.assertTrue(response.getBody().contains("\"status\":500"));
        Assertions.assertFalse(response.getBody().contains("Exception"));
    }

    @Test
    @DisplayName("쿼리/경로 파라미터 타입이 맞지 않으면 500이 아니라 400으로 응답해야 한다")
    void typeMismatch_returnsBadRequestNotServerError() {
        // 공연 목록 조회는 인증 없이 허용되지만, id 경로 변수로 숫자가 아닌 값을 주면
        // MethodArgumentTypeMismatchException이 발생한다 - 클라이언트 입력 오류이므로 400이어야 함
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/performances/not-a-number", String.class);

        Assertions.assertEquals(400, response.getStatusCode().value());
        Assertions.assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        Assertions.assertTrue(response.getBody().contains("\"status\":400"));
    }
}
