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

// 장르 메뉴(?genre=) 필터 검증
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceGenreFilterTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("genre를 주면 그 장르 묶음에 속한 KOPIS 장르의 공연만 반환한다")
    void performanceList_filtersByGenreGroup() {
        // given: 뮤지컬/연극 묶음에 속하는 공연 2건, 다른 장르 1건
        String musical = savePerformance("뮤지컬");
        String play = savePerformance("연극");
        String concert = savePerformance("대중음악");

        // when
        ResponseEntity<Map> response =
                restTemplate.getForEntity("/api/v1/performances?genre=MUSICAL_PLAY&size=100", Map.class);

        // then: 뮤지컬·연극만 오고, 다른 장르는 섞이지 않음
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        List<Object> titles = data.stream().map(p -> p.get("title")).toList();
        Assertions.assertTrue(titles.contains(musical));
        Assertions.assertTrue(titles.contains(play));
        Assertions.assertFalse(titles.contains(concert));
        Assertions.assertTrue(data.stream().allMatch(p -> List.of("뮤지컬", "연극").contains(p.get("category"))));
    }

    @Test
    @DisplayName("없는 장르 값이면 400을 반환한다")
    void performanceList_unknownGenre_returns400() {
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/v1/performances?genre=UNKNOWN", String.class);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private String savePerformance(String category) {
        Performance performance = new Performance();
        String title = "genre-test-" + UUID.randomUUID().toString().substring(0, 8);
        performance.setApiId(title);
        performance.setTitle(title);
        performance.setCategory(category);
        performanceRepository.save(performance);
        return title;
    }
}
