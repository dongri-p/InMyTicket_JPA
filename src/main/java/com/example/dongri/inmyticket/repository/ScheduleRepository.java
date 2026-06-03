package com.example.dongri.inmyticket.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    
    // 특정 공연에 속한 모든 회차 일정을 조회하는 메서드
    @Query("select s from Schedule s where s.performance.id = :performanceId")
    List<Schedule> findByPerformanceId(@Param("performanceId") Long performanceId);

    /**
     * N+1 문제를 원천 차단하는 페치 조인 조회
     * Schedule을 가져올 때 연관된 performance와 hall을 한방 쿼리로 묶어서 즉시 로딩
     */
    @Query("select s from Schedule s " +
           "join fetch s.performance p " +
           "join fetch s.hall h " +
           "where p.id = :performanceId")
    List<Schedule> findSchedulesWithPerformanceAndHall(@Param("performanceId") Long performanceId);
}
