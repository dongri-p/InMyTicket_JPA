package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.support.TestFixtures;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

// 예매 전(로그인 전)에도 회차/좌석 현황을 볼 수 있어야 한다는 API 의도(ScheduleApiController 주석)와
// 실제 보안 설정이 어긋나 있던 문제(15차 발견) 검증
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ScheduleSeatsPublicAccessTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("JWT 없이도 특정 회차의 좌석 목록을 조회할 수 있다")
    void seatsBySchedule_isAccessibleWithoutAuthentication() {
        Seat seat = TestFixtures.createAndSaveAvailableSeat(scheduleRepository);

        ResponseEntity<String> response = restTemplate.getForEntity(
                "/api/v1/schedules/" + seat.getSchedule().getId() + "/seats", String.class);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
