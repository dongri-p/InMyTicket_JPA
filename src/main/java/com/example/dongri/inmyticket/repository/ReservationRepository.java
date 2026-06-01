package com.example.dongri.inmyticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Reservation;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    
}
