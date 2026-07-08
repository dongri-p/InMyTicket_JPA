package com.example.dongri.inmyticket.config;

import java.io.PrintWriter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.dongri.inmyticket.api.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 인증 자체가 안 된 요청(JWT 없음/무효)은 401로 응답 (인가 실패인 403과 구분)
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(401);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(objectMapper.writeValueAsString(new ErrorResponse(401, "인증이 필요합니다.")));
            }
        };
    }

    // 인증은 됐지만 권한이 없는 요청(예: 일반 회원이 관리자 API 호출)은 403으로 응답
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(403);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            try (PrintWriter writer = response.getWriter()) {
                writer.write(objectMapper.writeValueAsString(new ErrorResponse(403, "접근 권한이 없습니다.")));
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint(authenticationEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                // 회원가입, 로그인은 인증 없이 허용
                .requestMatchers(HttpMethod.POST, "/api/v1/members", "/api/v1/members/login").permitAll()
                // 공연 목록/상세 조회는 인증 없이 허용
                .requestMatchers(HttpMethod.GET, "/api/v1/performances", "/api/v1/performances/**").permitAll()
                // 관리자 전용 기능
                .requestMatchers(HttpMethod.POST, "/api/v1/performances/sync").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/schedules").hasRole("ADMIN")
                // 나머지는 모두 JWT 인증 필요
                .anyRequest().authenticated()
            )
            .addFilterBefore(new JwtAuthenticationFilter(jwtProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}