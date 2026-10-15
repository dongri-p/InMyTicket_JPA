package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.PerformanceRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 지역 필터(?region=) 검증
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceRegionFilterTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("region을 주면 그 권역에 속한 KOPIS 지역의 공연만 반환한다")
    void performanceList_filtersByRegionGroup() {
        // given: 다른 테스트 데이터와 섞이지 않도록 같은 태그를 제목에 붙이고 검색으로 조회
        String tag = "region-" + UUID.randomUUID().toString().substring(0, 8);
        String busan = save(tag, "부산광역시");
        String daegu = save(tag, "대구광역시");
        String seoul = save(tag, "서울특별시");
        String unknownArea = save(tag, null);

        // when
        List<Object> titles = titles(restTemplate.getForEntity(
                "/api/v1/performances?region=GYEONGSANG&keyword={k}&size=100", Map.class, tag));

        // then: 경상권만 오고, 다른 지역·지역 모르는 공연은 빠짐
        Assertions.assertEquals(List.of(busan, daegu), titles);
        Assertions.assertFalse(titles.contains(seoul));
        Assertions.assertFalse(titles.contains(unknownArea));
    }

    @Test
    @DisplayName("region이 없으면 지역을 모르는 공연까지 전부 반환한다")
    void performanceList_withoutRegion_includesAll() {
        String tag = "region-all-" + UUID.randomUUID().toString().substring(0, 8);
        String seoul = save(tag, "서울특별시");
        String unknownArea = save(tag, null);

        List<Object> titles = titles(restTemplate.getForEntity(
                "/api/v1/performances?keyword={k}&size=100", Map.class, tag));

        Assertions.assertEquals(List.of(seoul, unknownArea), titles);
    }

    @Test
    @DisplayName("없는 지역 값이면 400을 반환한다")
    void performanceList_unknownRegion_returns400() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/performances?region=UNKNOWN", String.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private String save(String tag, String area) {
        Performance performance = new Performance();
        String title = tag + "-" + UUID.randomUUID().toString().substring(0, 8);
        performance.setApiId(title);
        performance.setTitle(title);
        performance.setArea(area);
        performanceRepository.save(performance);
        return title;
    }

    private List<Object> titles(ResponseEntity<Map> response) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data.stream().map(p -> p.get("title")).toList();
    }
}
