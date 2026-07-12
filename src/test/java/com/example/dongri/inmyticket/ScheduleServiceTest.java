package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.ScheduleService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@SpringBootTest
public class ScheduleServiceTest {

    @Autowired private ScheduleService scheduleService;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private HallRepository hallRepository;
    @Autowired private ScheduleRepository scheduleRepository;

    private Performance performance;
    private Hall hall;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        performance = new Performance();
        performance.setApiId("schedule-test-" + suffix);
        performance.setTitle("스케줄테스트공연" + suffix);
        performanceRepository.save(performance);

        hall = new Hall();
        hall.setName("테스트공연장" + suffix);
        hall.setTotalSeats(100);
        hallRepository.save(hall);
    }

    @Test
    @DisplayName("회차를 등록하면 지정한 좌석 수만큼 좌석이 함께 생성된다")
    void saveSchedule_createsScheduleWithSeats() {
        // when
        Long scheduleId = scheduleService.saveSchedule(
                performance.getId(), hall.getId(), LocalDateTime.now().plusDays(1), 5);

        // then
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow();
        Assertions.assertEquals(5, schedule.getTotalSeatCount());
        Assertions.assertEquals(5, schedule.getAvailableSeatCount());

        Page<Seat> seats = scheduleService.findSeatsBySchedule(scheduleId, PageRequest.of(0, 20));
        Assertions.assertEquals(5, seats.getTotalElements());
    }

    @Test
    @DisplayName("존재하지 않는 공연 id로 회차를 등록하면 예외가 발생한다")
    void saveSchedule_withNonExistentPerformance_throwsIllegalArgument() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduleService.saveSchedule(999999L, hall.getId(), LocalDateTime.now().plusDays(1), 5));
    }

    @Test
    @DisplayName("존재하지 않는 공연장 id로 회차를 등록하면 예외가 발생한다")
    void saveSchedule_withNonExistentHall_throwsIllegalArgument() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduleService.saveSchedule(performance.getId(), 999999L, LocalDateTime.now().plusDays(1), 5));
    }

    @Test
    @DisplayName("공연장 정원을 초과하는 좌석 수로 회차를 등록하면 예외가 발생한다")
    void saveSchedule_exceedingHallCapacity_throwsIllegalArgument() {
        // given: setUp()에서 hall.totalSeats=100으로 생성됨
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduleService.saveSchedule(performance.getId(), hall.getId(), LocalDateTime.now().plusDays(1), 101));
    }

    @Test
    @DisplayName("특정 공연의 회차 목록을 조회할 수 있다")
    void findSchedulesByPerformance_returnsSchedules() {
        scheduleService.saveSchedule(performance.getId(), hall.getId(), LocalDateTime.now().plusDays(1), 3);
        scheduleService.saveSchedule(performance.getId(), hall.getId(), LocalDateTime.now().plusDays(2), 3);

        List<Schedule> schedules = scheduleService.findSchedulesByPerformance(performance.getId());

        Assertions.assertEquals(2, schedules.size());
    }

    @Test
    @DisplayName("존재하지 않는 공연의 회차 목록을 조회하면 예외가 발생한다")
    void findSchedulesByPerformance_withNonExistentPerformance_throwsIllegalArgument() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduleService.findSchedulesByPerformance(999999L));
    }

    @Test
    @DisplayName("존재하지 않는 회차의 좌석을 조회하면 예외가 발생한다")
    void findSeatsBySchedule_withNonExistentSchedule_throwsIllegalArgument() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> scheduleService.findSeatsBySchedule(999999L, PageRequest.of(0, 20)));
    }

    @Test
    @DisplayName("좌석 목록 조회는 페이지 크기를 넘는 좌석을 한 번에 반환하지 않는다")
    void findSeatsBySchedule_respectsPageSize() {
        Long scheduleId = scheduleService.saveSchedule(
                performance.getId(), hall.getId(), LocalDateTime.now().plusDays(1), 10);

        Page<Seat> firstPage = scheduleService.findSeatsBySchedule(scheduleId, PageRequest.of(0, 3));

        Assertions.assertEquals(3, firstPage.getContent().size());
        Assertions.assertEquals(10, firstPage.getTotalElements());
    }
}
