package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.service.BookingGroupScheduler;
import com.example.dongri.inmyticket.service.ScheduleService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// 목록 정렬용으로 저장하는 예매 상태 그룹(Performance.bookingGroup)이 회차 등록·시간 경과에 맞게 바뀌는지
@SpringBootTest
public class BookingGroupSchedulerTest {

    @Autowired private BookingGroupScheduler bookingGroupScheduler;
    @Autowired private ScheduleService scheduleService;
    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private HallRepository hallRepository;

    @Test
    @DisplayName("새 공연은 종료일 기준으로 오픈예정/공연종료로 저장된다")
    void newPerformance_groupByEndDate() {
        LocalDate today = LocalDate.now();
        Assertions.assertEquals(BookingStatus.UPCOMING.getCode(), save(today.plusDays(3), null).getBookingGroup());
        Assertions.assertEquals(BookingStatus.UPCOMING.getCode(), save(null, null).getBookingGroup());
        Assertions.assertEquals(BookingStatus.ENDED.getCode(), save(today.minusDays(1), null).getBookingGroup());
    }

    @Test
    @DisplayName("회차를 등록하면 스케줄러를 기다리지 않고 바로 예매중이 된다")
    void saveSchedule_makesOnSaleImmediately() {
        Performance performance = save(LocalDate.now().plusDays(30), null);
        Hall hall = new Hall();
        hall.setName("그룹테스트홀" + UUID.randomUUID());
        hall.setTotalSeats(10);
        hallRepository.save(hall);

        scheduleService.saveSchedule(performance.getId(), hall.getId(), LocalDateTime.now().plusDays(1), 5);

        Assertions.assertEquals(BookingStatus.ON_SALE.getCode(), reload(performance).getBookingGroup());
    }

    @Test
    @DisplayName("마지막 회차가 시작된 예매중 공연은 종료일에 따라 오픈예정/공연종료로 내려가고, 남은 회차가 있으면 그대로다")
    void refresh_demotesOnSaleWhoseLastScheduleStarted() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        Performance stillRunning = save(today.plusDays(5), now.minusMinutes(1));
        Performance ended = save(today.minusDays(1), now.minusMinutes(1));
        Performance hasNext = save(today.plusDays(5), now.plusDays(1));
        Assertions.assertEquals(BookingStatus.ON_SALE.getCode(), hasNext.getBookingGroup());

        bookingGroupScheduler.refreshBookingGroups();

        Assertions.assertEquals(BookingStatus.UPCOMING.getCode(), reload(stillRunning).getBookingGroup());
        Assertions.assertEquals(BookingStatus.ENDED.getCode(), reload(ended).getBookingGroup());
        Assertions.assertEquals(BookingStatus.ON_SALE.getCode(), reload(hasNext).getBookingGroup());
    }

    @Test
    @DisplayName("종료일이 지난 오픈예정 공연은 공연종료로 내려간다")
    void refresh_demotesUpcomingPastEndDate() {
        // 저장 시점엔 오픈예정이었는데 그 사이 날짜가 바뀐 상황: 그룹 1 + 어제 종료로 만들어 둠
        Performance performance = save(LocalDate.now().plusDays(1), null);
        performance.setEndDate(LocalDate.now().minusDays(1));
        performanceRepository.save(performance);
        Assertions.assertEquals(BookingStatus.UPCOMING.getCode(), reload(performance).getBookingGroup());

        bookingGroupScheduler.refreshBookingGroups();

        Assertions.assertEquals(BookingStatus.ENDED.getCode(), reload(performance).getBookingGroup());
    }

    // lastScheduleAt이 있으면 '그 시점에 회차가 등록돼 예매중이 된' 상태를 만들고, 시각만 원하는 값으로 둠
    private Performance save(LocalDate endDate, LocalDateTime lastScheduleAt) {
        Performance performance = new Performance();
        performance.setApiId("group-" + UUID.randomUUID());
        performance.setTitle("그룹테스트");
        performance.setEndDate(endDate);
        performance = performanceRepository.save(performance);
        if (lastScheduleAt != null) {
            // 저장(@PrePersist) 뒤에 바꿔야 그룹이 재계산되지 않고 '예매중'으로 남음
            performance.scheduleAdded(LocalDateTime.now().plusDays(1), LocalDateTime.now());
            performance.setLastScheduleAt(lastScheduleAt);
            performance = performanceRepository.save(performance);
        }
        return performance;
    }

    private Performance reload(Performance performance) {
        return performanceRepository.findById(performance.getId()).orElseThrow();
    }
}
