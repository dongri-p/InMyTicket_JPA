package com.example.dongri.inmyticket.config;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtProvider {

    // 토큰을 서명할 때 쓸 비밀 열쇠(원래는 yml에 숨겨야 하지만, 우선 테스트용으로 자동 생성)
    private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

    // 토큰 유효 시간 설정(1시간)
    private final long tokenValidityInMilliseconds = 3600000;

    // 로그인 성공 시 JWT 토큰을 구워주는 메서드
    public String createToken(Long memberId, String loginId, String role) {

        Date now = new Date();
        Date vaildity = new Date(now.getTime() + tokenValidityInMilliseconds);

        return Jwts.builder()
                .setSubject(loginId)
                .claim("memberId", memberId)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(vaildity)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }
    
}
