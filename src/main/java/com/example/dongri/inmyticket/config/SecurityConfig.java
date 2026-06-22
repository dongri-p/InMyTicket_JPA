package com.example.dongri.inmyticket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity // 스프링 시큐리티 설정 활성화
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCryptPasswordEncoder: 회원가입할 때 입력한 비밀번호(예: 1234)를 데이터베이스에 그대로 넣으면 
        // 해킹 위험이 있어서, 이걸 asdfqwer123...같은 복잡한 암호문으로 바꿔줌
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // 포스트맨으로 테스트할 때 방해되는 CSRF 보안 설정을 잠시 꺼둔다
            .csrf(csrf -> csrf.disable())
            
            // 우리는 세션을 쓰지 않고 'JWT 토큰'을 쓸 거니까 상태를 STATELESS(무상태)로 설정
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // API 접근 권한 설정
            .authorizeHttpRequests(auth -> auth
                // 회원가입이랑 로그인은 토큰이 없는 상태에서 들어와야 하니까 무조건 통과(permitAll)
                .requestMatchers("/api/v1/members", "/api/v1/members/login").permitAll()
                // 그 외 나머지 모든 예매, 결제 API는 로그인을 해야만 접근 가능하게 잠그기
                .anyRequest().authenticated()
            );
        return http.build();
    }
}
