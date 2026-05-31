package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScheduleService {
    
    private final ScheduleRepository scheduleRepository;
    private final PerformanceRepository performanceRepository;
    private final HallRepository hallRepository;

    @Transactional
    public Long saveSchedule(Long performanceId, Long hallId, LocalDateTime startTime, int totalSeatCount) {

        // 1. 엔티티 조회
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연입니다. id=" + performanceId));
        
        // 2. 공연장 조회
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연장입니다. id=" + hallId));

        // 3. 도메인 생성 메서드 호출
        Schedule schedule = Schedule.creatSchedule(performance, hall, startTime, totalSeatCount);

        // 4. 저장
        scheduleRepository.save(schedule);
        return schedule.getId();
    }

    // 특정 공연의 전체 회차 목록 조회
    public List<Schedule> findSchedulesByPerformance(Long performanceId) {
        return scheduleRepository.findByPerformanceId(performanceId);
    }
}
