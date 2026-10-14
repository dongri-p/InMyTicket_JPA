package com.example.dongri.inmyticket.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateMemberRequest {

    // @NotBlank는 공백'만' 있는 값만 막으므로, "ab cd"처럼 중간·앞뒤 공백이나 특수문자가 섞인 아이디는 패턴으로 막음
    // (로그인 요청에는 걸지 않음: 이 규칙 이전에 가입한 아이디도 로그인은 되어야 함)
    @NotBlank
    @Size(min = 4, max = 20)
    @Pattern(regexp = "^[A-Za-z0-9]+$", message = "아이디는 영문과 숫자만 사용할 수 있습니다.")
    private String loginId;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8, max = 100)
    private String password;

    @NotBlank
    @Size(max = 30)
    private String name;

}
