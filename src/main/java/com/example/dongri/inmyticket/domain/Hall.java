package com.example.dongri.inmyticket.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Hall {
    
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) // INSERT INTO HALL (hall_id, name, total_seats) VALUES (1, '세종문화회관 대극장', 100);
    @Column(name = "hall_id")
    private Long id;

    private String name;
    private String address;
    private int totalSeats;
}
