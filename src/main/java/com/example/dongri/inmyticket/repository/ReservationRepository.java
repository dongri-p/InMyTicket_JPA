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

    // 여러 예약을 한 번에 비관적 락으로 조회 (자동 만료 스케줄러가 후보 예약마다 개별 조회하는 N+1 방지)
    // ORDER BY로 락 획득 순서를 항상 id 오름차순으로 고정 — SeatRepository.findByIdInWithLock과 동일한 데드락 방지 패턴
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id in :ids order by r.id")
    List<Reservation> findByIdInWithLock(@Param("ids") List<Long> ids);

    // 결제 없이 일정 시간 이상 방치된 PENDING 예약 id 조회 (자동 해제 스케줄러용)
    // 엔티티가 아닌 id만 조회하는 이유: 후보를 엔티티로 읽으면 영속성 컨텍스트에 캐시되고, 이후 findByIdInWithLock이
    // DB에서 최신 행을 읽어와도 이미 관리 중인 엔티티를 덮어쓰지 않음 -> 그 사이 결제가 확정돼도 PENDING으로 보여
    // 결제된 예약을 만료시켜버림. id만 넘기면 락 조회가 엔티티를 처음 로딩하므로 항상 최신 상태를 봄
    @Query("select r.id from Reservation r where r.status = :status and r.reservedAt < :cutoff")
    List<Long> findIdsByStatusAndReservedAtBefore(@Param("status") ReservationStatus status,
                                                  @Param("cutoff") LocalDateTime cutoff);

    // 결제 시작 후 일정 시간 넘게 PROCESSING에 머문 예약 id 조회 (고착 복구 스케줄러용, id만 조회하는 이유는 위와 같음)
    @Query("select r.id from Reservation r where r.status = :status and r.processingStartedAt < :cutoff")
    List<Long> findIdsByStatusAndProcessingStartedAtBefore(@Param("status") ReservationStatus status,
                                                           @Param("cutoff") LocalDateTime cutoff);

    // 내 예매 목록 조회 - N+1 방지를 위해 좌석/회차/공연을 한 방에 페치 조인
    // payment는 없을 수도 있어서 left join fetch
    @Query("select distinct r from Reservation r " +
           "join fetch r.tickets t " +
           "join fetch t.seat s " +
           "join fetch s.schedule sch " +
           "join fetch sch.performance p " +
           "left join fetch r.payment pay " +
           "where r.member.id = :memberId " +
           "order by r.reservedAt desc")
    List<Reservation> findWithDetailsByMemberId(@Param("memberId") Long memberId);
}
