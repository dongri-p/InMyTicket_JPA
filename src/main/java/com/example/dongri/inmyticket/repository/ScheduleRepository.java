package com.example.dongri.inmyticket.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * 잔여 좌석 수를 DB 레벨에서 원자적으로 1 감소시킴
     * (좌석 락과 별개로 Schedule 로우를 잠그지 않고도 lost-update를 방지)
     */
    @Modifying
    @Query("update Schedule s set s.availableSeatCount = s.availableSeatCount - 1 " +
           "where s.id = :scheduleId and s.availableSeatCount > 0")
    int decrementAvailableSeatCount(@Param("scheduleId") Long scheduleId);

    /**
     * 예약 취소로 좌석이 반환될 때, 잔여 좌석 수를 DB 레벨에서 원자적으로 1 증가시킴
     * totalSeatCount를 넘지 않도록 가드
     */
    @Modifying
    @Query("update Schedule s set s.availableSeatCount = s.availableSeatCount + 1 " +
           "where s.id = :scheduleId and s.availableSeatCount < s.totalSeatCount")
    int incrementAvailableSeatCount(@Param("scheduleId") Long scheduleId);

    /**
     * N+1 문제를 원천 차단하는 페치 조인 조회
     * Schedule을 가져올 때 연관된 performance와 hall을 한방 쿼리로 묶어서 즉시 로딩
     */
    @Query("select s from Schedule s " +
           "join fetch s.performance p " +
           "join fetch s.hall h " +
           "where p.id = :performanceId")
    List<Schedule> findSchedulesWithPerformanceAndHall(@Param("performanceId") Long performanceId);

    /**
     * 공연 목록 한 페이지 분량의 공연별 '아직 시작 전인' 회차 수를 group by 한 방으로 조회
     * (공연마다 회차를 따로 세면 목록 크기만큼 쿼리가 나가는 N+1이 되므로 묶어서 조회)
     * 회차가 하나도 없는 공연은 결과에 포함되지 않음 -> 호출 측에서 0으로 처리
     */
    @Query("select s.performance.id as performanceId, count(s) as scheduleCount from Schedule s " +
           "where s.performance.id in :performanceIds and s.startTime > :now " +
           "group by s.performance.id")
    List<PerformanceScheduleCount> countUpcomingSchedules(@Param("performanceIds") List<Long> performanceIds,
                                                          @Param("now") LocalDateTime now);

    interface PerformanceScheduleCount {
        Long getPerformanceId();
        Long getScheduleCount();
    }
}
