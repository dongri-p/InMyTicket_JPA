package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.domain.Role;
import com.example.dongri.inmyticket.repository.MemberRepository;
import com.example.dongri.inmyticket.service.MemberService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

@SpringBootTest
public class MemberServiceTest {

    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;

    private Member newMember(String loginId, String email) {
        Member member = new Member();
        member.setLoginId(loginId);
        member.setPassword("password123");
        member.setName("가입테스터");
        member.setEmail(email);
        member.setRole(Role.USER);
        return member;
    }

    @Test
    @DisplayName("회원가입 시 비밀번호는 평문이 아닌 암호화된 값으로 저장된다")
    void join_storesEncodedPasswordNotPlainText() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Member member = newMember("joinUser" + suffix, "join" + suffix + "@test.com");

        Long id = memberService.join(member);

        Member saved = memberRepository.findById(id).orElseThrow();
        Assertions.assertNotEquals("password123", saved.getPassword());
    }

    @Test
    @DisplayName("이미 사용 중인 아이디로 가입하면 예외가 발생한다")
    void join_withDuplicateLoginId_throwsIllegalState() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String loginId = "dupLoginUser" + suffix;
        memberService.join(newMember(loginId, "first" + suffix + "@test.com"));

        Member duplicate = newMember(loginId, "second" + suffix + "@test.com");

        Assertions.assertThrows(IllegalStateException.class, () -> memberService.join(duplicate));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일로 가입하면 예외가 발생한다")
    void join_withDuplicateEmail_throwsIllegalState() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String email = "dupEmail" + suffix + "@test.com";
        memberService.join(newMember("firstUser" + suffix, email));

        Member duplicate = newMember("secondUser" + suffix, email);

        Assertions.assertThrows(IllegalStateException.class, () -> memberService.join(duplicate));
    }
}
