package com.example.dongri.inmyticket.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private static final int SAME_HALL_MIN_GAP_HOURS = 3;

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

        // 공연장 정원을 초과하는 좌석 수로 회차가 만들어지지 않도록 검증
        if (totalSeatCount > hall.getTotalSeats()) {
            throw new IllegalArgumentException(
                    "좌석 수가 공연장 정원을 초과했습니다. totalSeatCount=" + totalSeatCount + ", hallCapacity=" + hall.getTotalSeats());
        }

        // 이미 지난 시각의 회차는 예매가 바로 막히므로 등록하지 않음 (관리자 화면의 날짜 min은 우회할 수 있어 서버에서도 검사)
        // 지난 시각이면서 기간 밖인 경우에도 더 근본적인 이유인 이 문구가 나오도록 기간 검사보다 먼저 확인
        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("이미 지난 시각에는 회차를 등록할 수 없습니다. startTime=" + startTime);
        }

        // KOPIS 공연 기간 밖의 날짜로 회차가 만들어지지 않도록 검증 (기간을 모르는 공연은 검증하지 않음)
        LocalDate startDate = startTime.toLocalDate();
        if ((performance.getStartDate() != null && startDate.isBefore(performance.getStartDate()))
                || (performance.getEndDate() != null && startDate.isAfter(performance.getEndDate()))) {
            throw new IllegalArgumentException(
                    "공연 기간(" + performance.getStartDate() + " ~ " + performance.getEndDate() + ") 밖의 날짜에는 회차를 등록할 수 없습니다. startTime=" + startTime);
        }

        // 같은 공연장에 시간이 겹치는 회차가 생기지 않도록 검증.
        // 러닝타임을 저장하지 않아서, 보통 공연 길이(2~3시간)를 기준으로 시작 시각이 앞뒤 3시간 안이면 겹친다고 봄 (딱 3시간 차이는 허용)
        if (scheduleRepository.existsInHallBetween(hallId,
                startTime.minusHours(SAME_HALL_MIN_GAP_HOURS), startTime.plusHours(SAME_HALL_MIN_GAP_HOURS))) {
            throw new IllegalArgumentException(
                    "같은 공연장에 시작 시각이 " + SAME_HALL_MIN_GAP_HOURS + "시간 안인 회차가 이미 있습니다. startTime=" + startTime);
        }

        // 3. 도메인 주도 생성 메서드 호출 (여기에 파라미터로 받은 startTime을 순수하게 쏙 넣어주면 돼!)
        Schedule schedule = Schedule.createSchedule(performance, hall, startTime, totalSeatCount);

        // 4. 데이터베이스 저장
        scheduleRepository.save(schedule);
        
        return schedule.getId();
    }

    // 특정 공연의 전체 회차 목록 조회
    public List<Schedule> findSchedulesByPerformance(Long performanceId) {
        List<Schedule> schedules = scheduleRepository.findSchedulesWithPerformanceAndHall(performanceId);
        // 회차가 하나도 없을 때만 공연 자체가 존재하지 않는 것인지 확인 (정상 케이스는 조회 1회로 끝남)
        if (schedules.isEmpty() && !performanceRepository.existsById(performanceId)) {
            throw new IllegalArgumentException("존재하지 않는 공연입니다. id=" + performanceId);
        }
        return schedules;
    }

    // 특정 회차의 좌석 목록 조회 (비인증 공개 API이므로 페이지네이션 없이 전체 조회를 허용하면
    // 대량조회로 인한 부하 위험이 있어 size를 제한 - 공연 목록 API와 동일한 이유)
    public Page<Seat> findSeatsBySchedule(Long scheduleId, Pageable pageable) {
        Page<Seat> seats = seatRepository.findByScheduleId(scheduleId, pageable);
        // 좌석이 하나도 없을 때만 회차 자체가 존재하지 않는 것인지 확인 (정상 케이스는 조회 1회로 끝남)
        if (seats.getTotalElements() == 0 && scheduleRepository.findById(scheduleId).isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 회차입니다. id=" + scheduleId);
        }
        return seats;
    }
}
