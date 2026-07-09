package com.example.dongri.inmyticket.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    Optional<Performance> findByApiId(String apiId);

    // 여러 apiId 중 이미 DB에 존재하는 것만 한 번에 조회 (N+1 방지)
    @Query("select p.apiId from Performance p where p.apiId in :apiIds")
    List<String> findApiIdsIn(@Param("apiIds") List<String> apiIds);
}
