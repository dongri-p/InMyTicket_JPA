package com.example.dongri.inmyticket.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {
    
    Optional<Performance> findByApiId(String apiId);
}
