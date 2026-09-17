package com.example.dongri.inmyticket.config;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.ScheduleService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 로컬 개발용 테스트 데이터(공연장 + 회차 + 좌석) 자동 시딩.
// ddl-auto: create로 재시작마다 초기화되는 로컬에서만 필요한 더미 데이터라 local 프로필에서만 동작시킴
// (prod에는 실제 운영 데이터가 들어가야 하므로 절대 여기서 생성하면 안 됨)
@Slf4j
@Component
@RequiredArgsConstructor
@Profile("local")
@Order(2) // PerformanceDataInitializer(공연 동기화)가 먼저 끝난 뒤 그 결과(performanceId)를 참조해야 함
public class LocalTestDataInitializer implements ApplicationRunner {

    private static final int TEST_TOTAL_SEAT_COUNT = 20;

    private final HallRepository hallRepository;
    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleService scheduleService;

    @Override
    public void run(ApplicationArguments args) {
        if (scheduleRepository.count() > 0) {
            return;
        }

        Performance performance = performanceRepository.findAll(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElse(null);

        if (performance == null) {
            log.warn("동기화된 공연이 없어 로컬 테스트용 회차를 생성하지 못했습니다.");
            return;
        }

        Hall hall = hallRepository.findAll(PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseGet(this::createTestHall);

        // 실행 시점 기준 항상 미래가 되도록 상대 시각으로 계산 (고정 날짜는 시간이 지나면 과거가 되어 예매 자체가 막힘)
        LocalDateTime startTime = LocalDateTime.now().plusDays(30).with(LocalTime.of(19, 0));

        try {
            Long scheduleId = scheduleService.saveSchedule(performance.getId(), hall.getId(), startTime, TEST_TOTAL_SEAT_COUNT);
            log.info("로컬 테스트용 회차 자동 생성 완료. scheduleId={}, performanceId={}, hallId={}, startTime={}",
                    scheduleId, performance.getId(), hall.getId(), startTime);
        } catch (RuntimeException e) {
            log.warn("로컬 테스트용 회차 자동 생성 실패", e);
        }
    }

    private Hall createTestHall() {
        Hall hall = new Hall();
        hall.setName("세종문화회관 대극장");
        hall.setAddress("서울특별시 종로구 세종대로 175");
        hall.setTotalSeats(100);
        return hallRepository.save(hall);
    }
}
