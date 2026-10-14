package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LoginRequest {

    // 공백이 든 아이디는 로그인도 막음 (가입은 CreateMemberRequest에서 영문·숫자만 허용)
    // 영문·숫자 규칙 대신 공백만 거르는 이유: 관리자 아이디는 환경변수로 정해져 가입 규칙을 거치지 않음
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^\\S+$", message = "아이디에 공백을 넣을 수 없습니다.")
    private String loginId;

    @NotBlank
    @Size(max = 100)
    private String password;
}