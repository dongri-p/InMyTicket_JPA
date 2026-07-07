package com.example.dongri.inmyticket.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.config.JwtProvider;
import com.example.dongri.inmyticket.domain.Member;
import com.example.dongri.inmyticket.repository.MemberRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public Long join(Member member) {
        // DB에 저장하기전에 비밀번호를 암호화해서 덮어씌우기
        String encodedPassword = passwordEncoder.encode(member.getPassword());
        member.setPassword(encodedPassword);

        validateDuplicateMember(member);
        memberRepository.save(member);
        return member.getId();
    }

    private void validateDuplicateMember(Member member) {
        memberRepository.findByLoginId(member.getLoginId())
            .ifPresent(m -> {
                throw new IllegalStateException("이미 사용 중인 아이디입니다.");
            });
        memberRepository.findByEmail(member.getEmail())
            .ifPresent(m -> {
                throw new IllegalStateException("이미 사용 중인 이메일입니다.");
            });
    }

    public Member findOne(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

    }

    /**
     * 로그인 처리 및 JWT 토큰 발급
     */
    public String login(String loginId, String password) {
        // 1. 아이디로 회원 조회
        // 계정 존재 여부가 드러나지 않도록 아이디 없음/비밀번호 불일치를 동일한 메시지로 응답
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

        // 2. 비밀번호 검증 (암호화된 녀석과 사용자가 입력한 평문 매칭 테스트)
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 3. 비밀번호까지 맞으면 JWT 토큰 구워서 리턴
        return jwtProvider.createToken(member.getId(), member.getLoginId(), member.getRole().name());
    }
     
    
}
