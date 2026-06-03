package com.example.dongri.inmyticket.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Seat;

import jakarta.persistence.LockModeType;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    
    /**
     * 비관적 락을 걸고 좌석을 조회
     * 다른 트랜잭션이 이 로우를 수정하거나 조회하려 하면 대기하게 만듬 
     */
    // PESSIMISTIC_WRITE 데이터베이스의 for update 자물쇠.
    // 내 트렌젝션이 끝날 때까지 다른 사람들은 읽지도 쓰지도 말고 대기하라는 가장 강력한 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE) 
    @Query("select s from Seat s where s.id = :id")
    Optional<Seat> findByIdWithLock(@Param("id") Long id);
}
