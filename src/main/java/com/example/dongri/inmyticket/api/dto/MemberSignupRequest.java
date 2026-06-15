package com.example.dongri.inmyticket.api.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MemberSignupRequest {
    private String loginId;
    private String password;
    private String name;
    private String email;
}
