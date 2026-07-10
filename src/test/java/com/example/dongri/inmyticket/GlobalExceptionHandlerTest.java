package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.api.GlobalExceptionHandler;
import com.example.dongri.inmyticket.api.dto.ErrorResponse;

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

    // HTTP로 실제 도달 가능한 예외를 하나씩 전용 핸들러(400/409/403 등)로 옮길 때마다
    // "예상치 못한 예외" 테스트의 트리거를 다시 찾아야 하는 문제(whack-a-mole)를 피하기 위해
    // 포괄 500 핸들러 자체는 순수 단위 테스트로 직접 검증한다
    @Test
    @DisplayName("전용 핸들러가 없는 예외도 표준 에러 JSON 포맷(500)으로 응답해야 한다")
    void unexpectedException_returnsStandardErrorFormat() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ErrorResponse response = handler.handleException(new RuntimeException("내부 상세 오류"));

        Assertions.assertEquals(500, response.getStatus());
        Assertions.assertEquals("서버 내부 오류가 발생했습니다.", response.getMessage());
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

    @Test
    @DisplayName("요청 본문이 파싱 불가능한 JSON이면 500이 아니라 400으로 응답해야 한다")
    void malformedJsonBody_returnsBadRequestNotServerError() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> malformedJsonRequest = new HttpEntity<>("{ invalid json", headers);

        ResponseEntity<String> response =
                restTemplate.postForEntity("/api/v1/members", malformedJsonRequest, String.class);

        Assertions.assertEquals(400, response.getStatusCode().value());
        Assertions.assertTrue(response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_JSON));
        Assertions.assertTrue(response.getBody().contains("\"status\":400"));
    }
}
