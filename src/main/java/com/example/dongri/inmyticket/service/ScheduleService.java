package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Hall;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.Schedule;
import com.example.dongri.inmyticket.domain.Seat;
import com.example.dongri.inmyticket.repository.HallRepository;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.SeatRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ScheduleService {
    
    private final ScheduleRepository scheduleRepository;
    private final PerformanceRepository performanceRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public Long saveSchedule(Long performanceId, Long hallId, LocalDateTime startTime, int totalSeatCount) {
        // 1. 공연 엔티티 조회
        Performance performance = performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연입니다. id=" + performanceId));
        
        // 2. 공연장 엔티티 조회
        Hall hall = hallRepository.findById(hallId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연장입니다. id=" + hallId));

        // 3. 도메인 주도 생성 메서드 호출 (여기에 파라미터로 받은 startTime을 순수하게 쏙 넣어주면 돼!)
        Schedule schedule = Schedule.createSchedule(performance, hall, startTime, totalSeatCount);

        // 4. 데이터베이스 저장
        scheduleRepository.save(schedule);
        
        return schedule.getId();
    }

    // 특정 공연의 전체 회차 목록 조회
    public List<Schedule> findSchedulesByPerformance(Long performanceId) {
        return scheduleRepository.findSchedulesWithPerformanceAndHall(performanceId);
    }

    // 특정 회차의 좌석 목록 조회
    public List<Seat> findSeatsBySchedule(Long scheduleId) {
        List<Seat> seats = seatRepository.findByScheduleId(scheduleId);
        // 좌석이 하나도 없을 때만 회차 자체가 존재하지 않는 것인지 확인 (정상 케이스는 조회 1회로 끝남)
        if (seats.isEmpty() && scheduleRepository.findById(scheduleId).isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 회차입니다. id=" + scheduleId);
        }
        return seats;
    }
}
