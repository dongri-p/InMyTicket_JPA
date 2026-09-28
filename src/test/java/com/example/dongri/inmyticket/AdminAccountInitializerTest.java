package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.config.AdminAccountInitializer;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Role;
import com.example.dongri.inmyticket.repository.MemberRepository;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminAccountInitializerTest {

    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AdminAccountInitializer initializer;

    @BeforeEach
    void setUp() {
        initializer = new AdminAccountInitializer(memberRepository, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "adminLoginId", "admin");
        ReflectionTestUtils.setField(initializer, "adminPassword", "newPassword123!");
    }

    private Member existingAdmin(String rawPassword) {
        Member admin = new Member();
        admin.setLoginId("admin");
        admin.setPassword(passwordEncoder.encode(rawPassword));
        admin.setName("Admin");
        admin.setRole(Role.ADMIN);
        return admin;
    }

    @Test
    @DisplayName("관리자 계정이 없으면 설정된 비밀번호로 새로 생성한다")
    void run_withoutAdmin_createsAdmin() {
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.empty());

        initializer.run(null);

        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("설정된 비밀번호가 바뀌었으면 기존 관리자 계정의 비밀번호 해시를 갱신한다")
    void run_withChangedPassword_updatesHash() {
        Member admin = existingAdmin("oldPassword");
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));

        initializer.run(null);

        verify(memberRepository).save(admin);
        Assertions.assertTrue(passwordEncoder.matches("newPassword123!", admin.getPassword()));
    }

    @Test
    @DisplayName("비밀번호가 그대로면 기존 관리자 계정을 다시 저장하지 않는다")
    void run_withSamePassword_doesNothing() {
        Member admin = existingAdmin("newPassword123!");
        String originalHash = admin.getPassword();
        when(memberRepository.findByLoginId("admin")).thenReturn(Optional.of(admin));

        initializer.run(null);

        verify(memberRepository, never()).save(any(Member.class));
        Assertions.assertEquals(originalHash, admin.getPassword());
    }
}
