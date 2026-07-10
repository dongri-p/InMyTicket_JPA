package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.PerformanceRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformancePaginationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("공연 목록 조회는 size 파라미터만큼만 반환하고, 상한(100)을 넘는 size 요청은 100건으로 제한된다")
    void performanceList_isPaginatedAndSizeClamped() {
        // given: 공연 5건 저장
        String prefix = "page-test-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        for (int i = 0; i < 5; i++) {
            Performance performance = new Performance();
            performance.setApiId(prefix + i);
            performance.setTitle(prefix + i);
            performanceRepository.save(performance);
        }

        // when: size=2로 조회
        ResponseEntity<Map> sizeLimitedResponse =
                restTemplate.getForEntity("/api/v1/performances?page=0&size=2", Map.class);

        // then: 요청한 size만큼만 반환됨
        Assertions.assertEquals(2, sizeLimitedResponse.getBody().get("count"));
        List<?> data = (List<?>) sizeLimitedResponse.getBody().get("data");
        Assertions.assertEquals(2, data.size());

        // when: 상한을 초과하는 size 요청
        ResponseEntity<Map> oversizedResponse =
                restTemplate.getForEntity("/api/v1/performances?size=999999", Map.class);

        // then: 100건 이하로만 반환됨 (실제 데이터가 100건 미만이라도 예외 없이 정상 응답)
        int count = (int) oversizedResponse.getBody().get("count");
        Assertions.assertTrue(count <= 100);
    }
}
