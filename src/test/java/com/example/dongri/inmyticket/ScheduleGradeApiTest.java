package com.example.dongri.inmyticket;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;

// 관리자 회차 등록 API의 등급 구역(grades) 요청 - 등급·가격이 좌석 조회 응답까지 그대로 이어지는지, 잘못된 값은 400인지
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ScheduleGradeApiTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private HallRepository hallRepository;

    private String adminToken;
    private Long performanceId;
    private Long hallId;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        adminToken = jwtProvider.createToken(1L, "gradeAdmin", "ADMIN");

        Performance performance = new Performance();
        performance.setApiId("grade-test-" + suffix);
        performance.setTitle("등급테스트공연" + suffix);
        performanceId = performanceRepository.save(performance).getId();

        Hall hall = new Hall();
        hall.setName("등급테스트홀" + suffix);
        hall.setTotalSeats(10);
        hallId = hallRepository.save(hall).getId();
    }

    @Test
    @DisplayName("등급 구역을 담아 회차를 등록하면 좌석 조회에 그 등급과 가격이 그대로 나온다")
    void createWithGrades_seatsCarryGradeAndPrice() {
        ResponseEntity<Map> created = post(scheduleBody(5, List.of(
                Map.of("grade", "VIP", "seatCount", 2, "price", 200000),
                Map.of("grade", "A", "seatCount", 3, "price", 50000))));
        Assertions.assertEquals(HttpStatus.CREATED, created.getStatusCode(), String.valueOf(created.getBody()));

        URI seatsUri = URI.create(created.getHeaders().getLocation() + "/seats?size=10");
        List<Map<String, Object>> seats = (List<Map<String, Object>>) restTemplate.getForEntity(seatsUri, Map.class).getBody().get("data");
        Assertions.assertEquals(List.of("VIP", "VIP", "A", "A", "A"), seats.stream().map(s -> s.get("grade")).toList());
        Assertions.assertEquals(List.of(200000, 200000, 50000, 50000, 50000), seats.stream().map(s -> s.get("price")).toList());
    }

    @Test
    @DisplayName("등급 구역 값이 잘못되면 400으로 거절한다 (0석, 없는 등급, 합계 불일치)")
    void createWithInvalidGrades_badRequest() {
        ResponseEntity<Map> zeroSeats = post(scheduleBody(5, List.of(
                Map.of("grade", "VIP", "seatCount", 0, "price", 150000),
                Map.of("grade", "R", "seatCount", 5, "price", 120000))));
        ResponseEntity<Map> unknownGrade = post(scheduleBody(5, List.of(
                Map.of("grade", "B", "seatCount", 5, "price", 150000))));
        ResponseEntity<Map> sumMismatch = post(scheduleBody(5, List.of(
                Map.of("grade", "VIP", "seatCount", 4, "price", 150000))));

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, zeroSeats.getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, unknownGrade.getStatusCode());
        Assertions.assertEquals(HttpStatus.BAD_REQUEST, sumMismatch.getStatusCode());
        Assertions.assertEquals("INVALID_SCHEDULE", sumMismatch.getBody().get("code"));
    }

    private Map<String, Object> scheduleBody(int totalSeatCount, List<Map<String, Object>> grades) {
        return Map.of(
                "performanceId", performanceId,
                "hallId", hallId,
                "startTime", LocalDateTime.now().plusDays(30).withNano(0).toString().replace('T', ' '),
                "totalSeatCount", totalSeatCount,
                "grades", grades);
    }

    private ResponseEntity<Map> post(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity("/api/v1/schedules", new HttpEntity<>(body, headers), Map.class);
    }
}
