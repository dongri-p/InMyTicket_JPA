package com.example.dongri.inmyticket.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dongri.inmyticket.domain.Hall;

public interface HallRepository extends JpaRepository<Hall, Long> {
    // 기본 CRUD(save나 findById 등) 기능은 JpaRepository가 자동으로 제공
}
