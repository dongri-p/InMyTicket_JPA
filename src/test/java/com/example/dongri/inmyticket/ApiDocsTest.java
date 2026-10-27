package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// Swagger 문서가 토큰 없이 열리고, 실제 응답 규칙(생성 201 + Location, 공통 에러 형식)과 같게 그려지는지 확인
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ApiDocsTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("Swagger UI와 OpenAPI 문서는 토큰 없이 열린다 (nginx가 넘기는 /api 아래)")
    void docs_arePublic() {
        Assertions.assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("/api/swagger-ui/index.html", String.class).getStatusCode());
        Assertions.assertEquals(HttpStatus.OK,
                restTemplate.getForEntity("/api/docs/openapi", String.class).getStatusCode());
    }

    @Test
    @DisplayName("생성 API는 201 + Location으로, 모든 API에 공통 에러 형식이 문서화된다")
    void docs_matchResponseRules() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/docs/openapi", String.class);
        JsonNode docs = objectMapper.readTree(response.getBody());

        JsonNode reserve = docs.at("/paths/~1api~1v1~1reservations/post/responses");
        Assertions.assertFalse(reserve.has("200"));
        Assertions.assertTrue(reserve.at("/201/headers").has("Location"));
        Assertions.assertTrue(docs.at("/paths/~1api~1v1~1reservations~1{reservationId}~1cancel/post/responses").has("204"));
        Assertions.assertEquals("#/components/schemas/ErrorResponse",
                docs.at("/paths/~1api~1v1~1payments/post/responses/default/content/application~1json/schema/$ref").asText());

        // 에러 코드 표는 enum에서 만들어지므로 모든 코드가 문서에 있어야 함
        String description = docs.at("/info/description").asText();
        for (ErrorCode code : ErrorCode.values()) {
            Assertions.assertTrue(description.contains("`" + code.name() + "`"), code.name());
        }
    }
}
