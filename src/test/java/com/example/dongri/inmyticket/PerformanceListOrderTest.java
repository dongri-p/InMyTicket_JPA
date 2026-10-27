package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 공연 목록 정렬: 예매중 -> 오픈예정(기간 모름 포함) -> 공연종료, 같은 그룹은 id 순 + 예매 상태 필터
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PerformanceListOrderTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("예매 가능한 회차가 있는 공연이 먼저, 끝난 공연이 맨 뒤에 온다")
    void performanceList_bookableFirst_endedLast() {
        // given: 다른 테스트 데이터와 섞이지 않도록 같은 태그를 제목에 붙이고 검색으로 조회
        String tag = "order-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now();

        // id가 작은 순서대로 저장 -> id 순이었다면 이 순서로 나옴
        String ended = save(tag + "-ended", today.minusDays(10), today.minusDays(1));
        String endedWithPastSchedule = save(tag + "-ended-past", today.minusDays(10), today.minusDays(1));
        addSchedule(endedWithPastSchedule, LocalDateTime.now().minusDays(2));
        String upcoming = save(tag + "-upcoming", today.plusDays(5), today.plusDays(10));
        String unknownPeriod = save(tag + "-unknown", null, null);
        String endsToday = save(tag + "-ends-today", today.minusDays(3), today);
        String bookable = save(tag + "-bookable", today, today.plusDays(10));
        addSchedule(bookable, LocalDateTime.now().plusDays(1));

        // when
        List<Object> titles = titles(restTemplate.getForEntity(
                "/api/v1/performances?keyword={k}&size=100", Map.class, tag));

        // then: 오늘 끝나는 공연은 아직 진행 중, 지난 회차만 있는 끝난 공연은 예매중이 아님
        Assertions.assertEquals(
                List.of(bookable, upcoming, unknownPeriod, endsToday, ended, endedWithPastSchedule),
                titles);
    }

    @Test
    @DisplayName("nextCursor로 이어 받으면 정렬 순서대로 중복·누락 없이 끝까지 오고, 마지막 페이지엔 nextCursor가 없다")
    void performanceList_cursorPagesDoNotOverlap() {
        String tag = "order-page-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 5; i++) {
            String title = save(tag + "-" + i, today.minusDays(10), i % 2 == 0 ? today.minusDays(1) : today.plusDays(5));
            if (i == 4) {
                addSchedule(title, LocalDateTime.now().plusDays(1));
            }
        }

        Map<String, Object> page0 = page(tag, null);
        Map<String, Object> page1 = page(tag, (String) page0.get("nextCursor"));
        Map<String, Object> page2 = page(tag, (String) page1.get("nextCursor"));

        // 4(예매중) -> 1, 3(진행 예정) -> 0, 2(종료)
        Assertions.assertEquals(List.of(tag + "-4", tag + "-1"), titlesOf(page0));
        Assertions.assertEquals(List.of(tag + "-3", tag + "-0"), titlesOf(page1));
        Assertions.assertEquals(List.of(tag + "-2"), titlesOf(page2));
        Assertions.assertNull(page2.get("nextCursor"));
        // 전체 건수는 첫 페이지에서만
        Assertions.assertEquals(5, ((Number) page0.get("totalCount")).intValue());
        Assertions.assertNull(page1.get("totalCount"));
    }

    @Test
    @DisplayName("첫 페이지를 받은 뒤 앞쪽에 공연이 추가돼도 다음 페이지에 이미 본 공연이 다시 나오지 않는다 (OFFSET이면 중복)")
    void performanceList_insertBeforeCursor_noDuplicate() {
        String tag = "order-insert-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 4; i++) {
            save(tag + "-" + i, today.plusDays(5), today.plusDays(10));
        }
        Map<String, Object> page0 = page(tag, null);

        // 첫 페이지를 본 사이에 맨 앞(예매중) 그룹에 공연이 생김
        String newBookable = save(tag + "-new", today, today.plusDays(10));
        addSchedule(newBookable, LocalDateTime.now().plusDays(1));
        Map<String, Object> page1 = page(tag, (String) page0.get("nextCursor"));

        Assertions.assertEquals(List.of(tag + "-0", tag + "-1"), titlesOf(page0));
        Assertions.assertEquals(List.of(tag + "-2", tag + "-3"), titlesOf(page1));
    }

    @Test
    @DisplayName("형식이 틀린 커서는 400이다")
    void performanceList_invalidCursor_badRequest() {
        for (String cursor : List.of("not-base64!!", "OTo5", "YWJj")) {  // 9:9(없는 그룹), abc
            ResponseEntity<Map> response = restTemplate.getForEntity("/api/v1/performances?cursor={c}", Map.class, cursor);
            Assertions.assertEquals(400, response.getStatusCode().value(), cursor);
        }
    }

    private Map<String, Object> page(String keyword, String cursor) {
        String url = "/api/v1/performances?keyword={k}&size=2" + (cursor == null ? "" : "&cursor=" + cursor);
        return restTemplate.getForEntity(url, Map.class, keyword).getBody();
    }

    private List<Object> titlesOf(Map<String, Object> body) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        return data.stream().map(p -> p.get("title")).toList();
    }

    @Test
    @DisplayName("status로 예매중·오픈예정·공연종료 공연만 골라 볼 수 있고, 잘못된 값은 400이다")
    void performanceList_filterByBookingStatus() {
        String tag = "status-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now();
        String ended = save(tag + "-ended", today.minusDays(10), today.minusDays(1));
        String upcoming = save(tag + "-upcoming", today.plusDays(5), today.plusDays(10));
        String unknownPeriod = save(tag + "-unknown", null, null);
        String bookable = save(tag + "-bookable", today, today.plusDays(10));
        addSchedule(bookable, LocalDateTime.now().plusDays(1));

        Assertions.assertEquals(List.of(bookable), titlesWithStatus(tag, "ON_SALE"));
        Assertions.assertEquals(List.of(upcoming, unknownPeriod), titlesWithStatus(tag, "UPCOMING"));
        Assertions.assertEquals(List.of(ended), titlesWithStatus(tag, "ENDED"));

        // 응답의 bookingStatus도 필터와 같은 값 (JPQL 판정과 Java 판정이 어긋나지 않는지)
        for (String status : List.of("ON_SALE", "UPCOMING", "ENDED")) {
            Assertions.assertEquals(List.of(status), bookingStatusesWithStatus(tag, status).stream().distinct().toList());
        }

        // totalCount도 필터가 적용된 건수 (카운트 쿼리에 같은 조건이 들어갔는지)
        ResponseEntity<Map> onSale = restTemplate.getForEntity(
                "/api/v1/performances?keyword={k}&status=ON_SALE&size=1", Map.class, tag);
        Assertions.assertEquals(1, ((Number) onSale.getBody().get("totalCount")).intValue());

        ResponseEntity<Map> invalid = restTemplate.getForEntity("/api/v1/performances?status=SOLD_OUT", Map.class);
        Assertions.assertEquals(400, invalid.getStatusCode().value());
    }

    private List<Object> titlesWithStatus(String keyword, String status) {
        return titles(restTemplate.getForEntity(
                "/api/v1/performances?keyword={k}&status={s}&size=100", Map.class, keyword, status));
    }

    private List<Object> bookingStatusesWithStatus(String keyword, String status) {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                "/api/v1/performances?keyword={k}&status={s}&size=100", Map.class, keyword, status);
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data.stream().map(p -> p.get("bookingStatus")).toList();
    }

    @Test
    @DisplayName("공연 상세도 목록과 같은 기준의 bookingStatus를 내려준다")
    void performanceDetail_bookingStatus() {
        String tag = "detail-" + UUID.randomUUID().toString().substring(0, 8);
        LocalDate today = LocalDate.now();
        String bookable = save(tag + "-bookable", today, today.plusDays(10));
        addSchedule(bookable, LocalDateTime.now().plusDays(1));
        String ended = save(tag + "-ended", today.minusDays(10), today.minusDays(1));

        Assertions.assertEquals("ON_SALE", detail(bookable).get("bookingStatus"));
        Assertions.assertEquals("ENDED", detail(ended).get("bookingStatus"));
    }

    private Map<String, Object> detail(String title) {
        Long id = performanceRepository.findAll().stream()
                .filter(p -> title.equals(p.getTitle()))
                .findFirst()
                .orElseThrow()
                .getId();
        return restTemplate.getForEntity("/api/v1/performances/{id}", Map.class, id).getBody();
    }

    private List<Object> titles(ResponseEntity<Map> response) {
        List<Map<String, Object>> data = (List<Map<String, Object>>) response.getBody().get("data");
        return data.stream().map(p -> p.get("title")).toList();
    }

    private String save(String title, LocalDate startDate, LocalDate endDate) {
        Performance performance = new Performance();
        performance.setApiId("order-" + UUID.randomUUID());
        performance.setTitle(title);
        performance.setCategory("뮤지컬");
        performance.setStartDate(startDate);
        performance.setEndDate(endDate);
        performanceRepository.save(performance);
        return title;
    }

    private void addSchedule(String performanceTitle, LocalDateTime startTime) {
        Performance performance = performanceRepository.findAll().stream()
                .filter(p -> performanceTitle.equals(p.getTitle()))
                .findFirst()
                .orElseThrow();
        Schedule schedule = new Schedule();
        schedule.setPerformance(performance);
        schedule.setStartTime(startTime);
        schedule.setTotalSeatCount(1);
        schedule.setAvailableSeatCount(1);
        scheduleRepository.save(schedule);
    }
}
