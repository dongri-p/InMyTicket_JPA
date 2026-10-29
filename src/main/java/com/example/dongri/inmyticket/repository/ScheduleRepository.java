package com.example.dongri.inmyticket.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    /**
     * N+1 문제를 원천 차단하는 페치 조인 조회
     * Schedule을 가져올 때 연관된 performance와 hall을 한방 쿼리로 묶어서 즉시 로딩
     * 상세 화면 회차 목록이 등록 순서가 아니라 날짜순으로 보이도록 시작 시각 순 정렬 (같은 시각이면 id 순)
     */
    @Query("select s from Schedule s " +
           "join fetch s.performance p " +
           "join fetch s.hall h " +
           "where p.id = :performanceId " +
           "order by s.startTime, s.id")
    List<Schedule> findSchedulesWithPerformanceAndHall(@Param("performanceId") Long performanceId);

    /**
     * 같은 공연장에 시작 시각이 (from, to) 사이(경계 제외)인 회차가 있는지 — 회차 등록 시 겹치는 회차 검사용
     */
    @Query("select count(s) > 0 from Schedule s " +
           "where s.hall.id = :hallId and s.startTime > :from and s.startTime < :to")
    boolean existsInHallBetween(@Param("hallId") Long hallId,
                                @Param("from") LocalDateTime from,
                                @Param("to") LocalDateTime to);

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
