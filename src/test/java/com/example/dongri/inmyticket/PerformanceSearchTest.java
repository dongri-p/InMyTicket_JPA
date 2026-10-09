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

import java.util.List;
import java.util.Map;
import java.util.UUID;

// 헤더 검색창(?keyword=) 검증
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceSearchTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("keyword를 주면 제목에 검색어가 포함된 공연만 반환한다 (대소문자 무시)")
    void performanceList_searchByTitle() {
        // given: 검색어가 들어간 공연 1건, 안 들어간 공연 1건
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String hit = savePerformance("Search-" + tag + " 오페라", "서양음악(클래식)");
        String miss = savePerformance("other-" + tag, "서양음악(클래식)");

        // when: 소문자로 검색
        List<Object> titles = search("search-" + tag);

        // then
        Assertions.assertTrue(titles.contains(hit));
        Assertions.assertFalse(titles.contains(miss));
    }

    @Test
    @DisplayName("keyword와 genre를 같이 주면 둘 다 만족하는 공연만 반환한다")
    void performanceList_searchWithGenre() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String musical = savePerformance("both-" + tag, "뮤지컬");
        String concert = savePerformance("both-" + tag + "-concert", "대중음악");

        List<Object> titles = titles(restTemplate.getForEntity("/api/v1/performances?genre=MUSICAL_PLAY&keyword={k}&size=100", Map.class, "both-" + tag));

        Assertions.assertTrue(titles.contains(musical));
        Assertions.assertFalse(titles.contains(concert));
    }

    @Test
    @DisplayName("검색어의 %는 와일드카드가 아니라 글자 그대로 검색한다")
    void performanceList_percentIsLiteral() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        savePerformance("pct-" + tag, "뮤지컬");

        // '%'가 와일드카드로 해석되면 위 공연이 걸림
        List<Object> titles = search("pct%" + tag);

        Assertions.assertTrue(titles.isEmpty());
    }

    @Test
    @DisplayName("공백만 있는 keyword는 검색 없이 전체 목록을 반환한다")
    void performanceList_blankKeyword_returnsAll() {
        savePerformance("blank-" + UUID.randomUUID().toString().substring(0, 8), "뮤지컬");

        ResponseEntity<Map> all = restTemplate.getForEntity("/api/v1/performances?size=1", Map.class);
        ResponseEntity<Map> blank = restTemplate.getForEntity("/api/v1/performances?keyword={k}&size=1", Map.class, "  ");

        Assertions.assertEquals(all.getBody().get("totalCount"), blank.getBody().get("totalCount"));
    }

    // 검색어는 URI 변수로 넘겨야 한 번만 인코딩됨 (URL 문자열에 %20을 직접 쓰면 %2520으로 이중 인코딩)
    private List<Object> search(String keyword) {
        return titles(restTemplate.getForEntity("/api/v1/performances?keyword={k}&size=100", Map.class, keyword));
    }

    private List<Object> titles(ResponseEntity<Map> response) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data.stream().map(p -> p.get("title")).toList();
    }

    private String savePerformance(String title, String category) {
        Performance performance = new Performance();
        performance.setApiId("search-" + UUID.randomUUID());
        performance.setTitle(title);
        performance.setCategory(category);
        performanceRepository.save(performance);
        return title;
    }
}
