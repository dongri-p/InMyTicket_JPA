package com.example.dongri.inmyticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Payment;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
}
