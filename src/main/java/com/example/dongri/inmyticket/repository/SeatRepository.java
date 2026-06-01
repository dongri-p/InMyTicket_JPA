package com.example.dongri.inmyticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Seat;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    
}
