package com.example.dongri.inmyticket.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.CreateMemberResponse;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Role;
import com.example.dongri.inmyticket.service.MemberService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;
    
    @PostMapping("/api/v1/members")
    public CreateMemberResponse saveMemberV1(@RequestBody CreateMemberRequest request) {

        Member member = new Member();
        member.setEmail(request.getEmail());
        member.setPassword(request.getPassword());
        member.setName(request.getName());
        member.setRole(Role.USER);

        Long id = memberService.join(member);
        return new CreateMemberResponse(id);

    }
}
