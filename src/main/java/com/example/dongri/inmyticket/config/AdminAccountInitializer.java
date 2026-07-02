package com.example.dongri.inmyticket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Role;
import com.example.dongri.inmyticket.repository.MemberRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 회원가입 API는 항상 Role.USER로만 가입시키므로, ADMIN 계정은 기동 시 여기서 최초 1회 생성한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.login-id}")
    private String adminLoginId;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (memberRepository.findByLoginId(adminLoginId).isPresent()) {
            return;
        }

        Member admin = new Member();
        admin.setLoginId(adminLoginId);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);
        memberRepository.save(admin);

        log.info("관리자 계정이 생성되었습니다. loginId={}", adminLoginId);
    }
}
