package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateHallRequest {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String address;

    @Min(1)
    @Max(100000)
    private int totalSeats;
}
