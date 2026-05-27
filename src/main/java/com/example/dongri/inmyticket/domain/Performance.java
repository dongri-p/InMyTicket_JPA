package com.example.dongri.inmyticket.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Performance {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "performance_id")
    private Long id;

    private String apiId;
    private String title;
    private String artist;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;
    private String status;

    private LocalDateTime lastUpdatedAt;
    
}
