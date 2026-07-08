package com.example.dongri.inmyticket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GlobalExceptionHandlerTest {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("예상치 못한 예외도 표준 에러 JSON 포맷(500)으로 응답해야 한다")
    void unexpectedException_returnsStandardErrorFormat() {
        // 공연 목록 조회는 인증 없이 허용되지만, id 경로 변수로 숫자가 아닌 값을 주면
        // 컨트롤러에서 처리하지 않은 타입 변환 예외가 발생한다
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/performances/not-a-number", String.class);

        Assertions.assertEquals(500, response.getStatusCode().value());
        Assertions.assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        Assertions.assertTrue(response.getBody().contains("\"status\":500"));
        Assertions.assertFalse(response.getBody().contains("Exception"));
    }
}
