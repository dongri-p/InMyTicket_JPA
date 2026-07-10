package com.example.dongri.inmyticket.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.CreateMemberRequest;
import com.example.dongri.inmyticket.api.dto.CreateResourceResponse;
import com.example.dongri.inmyticket.api.dto.LoginRequest;
import com.example.dongri.inmyticket.api.dto.LoginResponse;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Role;
import com.example.dongri.inmyticket.service.MemberService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class MemberApiController {

    private final MemberService memberService;
    
    @PostMapping("/api/v1/members")
    public CreateResourceResponse saveMemberV1(@Validated @RequestBody CreateMemberRequest request) {

        Member member = new Member();
        member.setLoginId(request.getLoginId());
        member.setEmail(request.getEmail());
        member.setPassword(request.getPassword());
        member.setName(request.getName());
        member.setRole(Role.USER);

        Long id = memberService.join(member);
        return new CreateResourceResponse(id, "회원가입이 완료되었습니다.");
    }

    @PostMapping("/api/v1/members/login")
    public LoginResponse login(@Validated @RequestBody LoginRequest request) {
        // 로그인 검증 후 토큰 받아오기
        String token = memberService.login(request.getLoginId(), request.getPassword());
        
        // 포스트맨에게 토큰이 담긴 응답 전달!
        return new LoginResponse(token);
    }
}
