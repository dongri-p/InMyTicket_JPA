package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PerformanceService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// 공연 목록의 '예매중/오픈예정' 표시에 쓰는 회차 수 집계 검증
@SpringBootTest
public class PerformanceUpcomingScheduleCountTest {

    @Autowired private PerformanceService performanceService;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ScheduleRepository scheduleRepository;

    @Test
    @DisplayName("공연별 회차 수는 아직 시작 전인 회차만 세고, 회차가 없는 공연은 결과에 포함되지 않는다")
    void countUpcomingSchedules_countsOnlyFutureSchedulesPerPerformance() {
        // given: 미래 회차 2개 + 지난 회차 1개인 공연, 회차가 없는 공연
        Performance withSchedules = savePerformance();
        Performance withoutSchedules = savePerformance();
        LocalDateTime now = LocalDateTime.now();
        scheduleRepository.save(Schedule.createSchedule(withSchedules, null, now.plusDays(1), 1));
        scheduleRepository.save(Schedule.createSchedule(withSchedules, null, now.plusDays(2), 1));
        scheduleRepository.save(Schedule.createSchedule(withSchedules, null, now.minusDays(1), 1));

        // when
        Map<Long, Long> counts = performanceService.countUpcomingSchedules(
                List.of(withSchedules.getId(), withoutSchedules.getId()));

        // then
        Assertions.assertEquals(2L, counts.get(withSchedules.getId()));
        Assertions.assertFalse(counts.containsKey(withoutSchedules.getId()));
    }

    @Test
    @DisplayName("빈 목록이면 쿼리 없이 빈 결과를 반환한다")
    void countUpcomingSchedules_emptyIds() {
        Assertions.assertTrue(performanceService.countUpcomingSchedules(List.of()).isEmpty());
    }

    private Performance savePerformance() {
        Performance performance = new Performance();
        String apiId = "count-test-" + UUID.randomUUID().toString().substring(0, 8);
        performance.setApiId(apiId);
        performance.setTitle(apiId);
        return performanceRepository.save(performance);
    }
}
