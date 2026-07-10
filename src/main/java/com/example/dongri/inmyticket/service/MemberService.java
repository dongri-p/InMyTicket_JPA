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

    // 존재하지 않는 loginId용 더미 BCrypt 해시 (특정 평문과 무관, 형식만 유효하면 됨) -
    // 계정 미존재 시에도 동일하게 BCrypt 비교를 수행해 응답시간으로 계정 존재 여부가 드러나지 않게 함
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final LoginAttemptGuard loginAttemptGuard;

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

    /**
     * 로그인 처리 및 JWT 토큰 발급
     */
    public String login(String loginId, String password) {
        // 0. 반복된 로그인 실패가 있었다면 잠금 여부부터 확인 (무차별 대입 방지)
        loginAttemptGuard.checkNotLocked(loginId);

        // 1. 아이디로 회원 조회
        Member member = memberRepository.findByLoginId(loginId).orElse(null);

        // 2. 비밀번호 검증 - 계정이 없어도 더미 해시로 동일하게 BCrypt 비교를 수행해
        // 응답시간 차이로 계정 존재 여부가 드러나는 타이밍 사이드채널을 막음
        String hashToCompare = (member != null) ? member.getPassword() : DUMMY_PASSWORD_HASH;
        boolean passwordMatches = passwordEncoder.matches(password, hashToCompare);

        // 3. 아이디 없음/비밀번호 불일치를 동일한 메시지로 응답 (계정 존재 여부 노출 방지)
        if (member == null || !passwordMatches) {
            loginAttemptGuard.recordFailure(loginId);
            throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        // 4. 로그인 성공 시 실패 기록 초기화 후 JWT 토큰 구워서 리턴
        loginAttemptGuard.recordSuccess(loginId);
        return jwtProvider.createToken(member.getId(), member.getLoginId(), member.getRole().name());
    }
     
    
}
