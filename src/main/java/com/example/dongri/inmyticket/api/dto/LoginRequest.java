package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;
}