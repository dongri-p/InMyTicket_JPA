package com.example.dongri.inmyticket.config;

import com.example.dongri.inmyticket.api.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);

            try {
                Claims claims = jwtProvider.parseClaims(token);

                String loginId = claims.getSubject();
                String role = claims.get("role", String.class);
                Long memberId = claims.get("memberId", Long.class);

                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        new AuthenticatedMember(memberId, loginId),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (ExpiredJwtException e) {
                writeUnauthorized(response, "토큰이 만료되었습니다.");
                return;
            } catch (JwtException e) {
                writeUnauthorized(response, "유효하지 않은 토큰입니다.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    // SecurityConfig의 AuthenticationEntryPoint와 동일한 {status, message} JSON 포맷으로 응답.
    // response.sendError()를 쓰면 서블릿 컨테이너 기본 에러 페이지로 빠져 다른 401 응답과 포맷이 달라짐
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(new ErrorResponse(401, message)));
        }
    }
}