package com.example.dongri.inmyticket.config;

import java.util.Optional;

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
        Optional<Member> existing = memberRepository.findByLoginId(adminLoginId);

        if (existing.isPresent()) {
            Member admin = existing.get();
            // .env의 비밀번호가 바뀌었으면 기존 계정 해시를 갱신한다.
            // ApplicationRunner는 트랜잭션 밖이라 더티 체킹이 안 되므로 save()를 명시적으로 호출한다.
            if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                memberRepository.save(admin);
                log.info("관리자 비밀번호가 갱신되었습니다. loginId={}", adminLoginId);
            }
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
