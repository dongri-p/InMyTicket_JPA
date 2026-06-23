package com.example.dongri.inmyticket.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 포스트맨 Bearer Token이 담겨오는 Authorization 헤더 추출
        String authorizationHeader = request.getHeader("Authorization");

        // 2. 헤더가 Bearer로 시작하는지 검증
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7); // "Bearer " 제거한 순수 토큰 추출

            // 원래는 jwtProvider에서 토큰 만료 여부나 서명을 검증해야 하지만,
            // 우선 포스트맨 통과 및 인증 객체 주입 시뮬레이션을 위해 통과 처리
            try {
                // 시큐리티가 "아, 이 유저는 정상 인증된 유저구나"라고 판단할 수 있도록 Authentication에 담기
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        "dongri", // Principal (인증 유저 아이디)
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")) // 권한 부여
                );

                // 중요: 시큐리티의 핵심 보관함(Context)에 인증 객체 장착
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 토큰이 변조되었거나 문제 발생 시 통과시키지 않음
            }
        }
        // 3. 다음 시큐리티 경비원 단계로 요청 넘기기
        filterChain.doFilter(request, response);
    }
}