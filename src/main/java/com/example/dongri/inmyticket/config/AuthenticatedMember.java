package com.example.dongri.inmyticket.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class AuthenticatedMember {
    private final Long memberId;
    private final String loginId;
}
