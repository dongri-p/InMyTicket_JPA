package com.example.dongri.inmyticket.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Reservation;
import com.example.dongri.inmyticket.domain.ReservationStatus;

import jakarta.persistence.LockModeType;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 비관적 락을 걸고 예약을 조회.
     * 취소(cancel)와 결제 승인(approve)이 같은 예약을 동시에 수정하지 못하도록 직렬화한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdWithLock(@Param("id") Long id);

    // 결제 없이 일정 시간 이상 방치된 PENDING 예약 조회 (자동 해제 스케줄러용)
    List<Reservation> findByStatusAndReservedAtBefore(ReservationStatus status, LocalDateTime cutoff);
}
