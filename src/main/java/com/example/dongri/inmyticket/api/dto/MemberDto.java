package com.example.dongri.inmyticket.api.dto;

import com.example.dongri.inmyticket.domain.Member;

import lombok.Getter;

// 비밀번호(해시)와 권한은 내려주지 않음
@Getter
public class MemberDto {

    private Long memberId;
    private String loginId;
    private String name;
    private String email;

    public MemberDto(Member member) {
        this.memberId = member.getId();
        this.loginId = member.getLoginId();
        this.name = member.getName();
        this.email = member.getEmail();
    }
}
