package com.example.dongri.inmyticket;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

// 운영에서는 모든 요청이 nginx 컨테이너를 거쳐 들어오므로, 프록시 헤더(X-Forwarded-For)를 읽지 않으면
// getRemoteAddr()가 항상 nginx IP가 됨 → 로그인 잠금 키(loginId, IP)의 IP가 모든 사용자에게 같아져
// 누구든 남의 계정을 5회 실패로 잠글 수 있음. 실제 Tomcat을 띄워(RANDOM_PORT) 프록시 헤더 처리까지 검증.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class LoginLockClientIpTest {

    private static final String PASSWORD = "correct1234!";

    @Autowired private TestRestTemplate restTemplate;

    @Test
    @DisplayName("프록시 뒤의 다른 클라이언트가 5회 실패해도 정상 사용자의 로그인은 잠기지 않는다")
    void attackerBehindProxy_cannotLockOutOtherClient() {
        String loginId = "ip" + UUID.randomUUID().toString().substring(0, 8);
        signup(loginId);

        // 공격자(203.0.113.10)가 nginx를 거쳐 틀린 비밀번호를 5회 전송
        for (int i = 0; i < 5; i++) {
            login(loginId, "wrong-password", "203.0.113.10");
        }

        // 공격자 IP는 잠김
        ResponseEntity<String> attacker = login(loginId, PASSWORD, "203.0.113.10");
        Assertions.assertTrue(attacker.getBody().contains("로그인 시도가 너무 많습니다"));

        // 같은 nginx를 거쳐 들어온 정상 사용자(198.51.100.20)는 로그인 가능
        ResponseEntity<String> owner = login(loginId, PASSWORD, "198.51.100.20");
        Assertions.assertEquals(200, owner.getStatusCode().value());
    }

    @Test
    @DisplayName("공격자가 X-Forwarded-For에 정상 사용자 IP를 위조해 넣어도 그 사용자는 잠기지 않는다")
    void spoofedForwardedFor_cannotLockOutVictimIp() {
        String loginId = "ip" + UUID.randomUUID().toString().substring(0, 8);
        signup(loginId);

        // 공격자가 "X-Forwarded-For: 198.51.100.20"을 위조해 보내면 nginx는 실제 IP를 뒤에 덧붙여 전달함
        for (int i = 0; i < 5; i++) {
            login(loginId, "wrong-password", "198.51.100.20, 203.0.113.10");
        }

        ResponseEntity<String> owner = login(loginId, PASSWORD, "198.51.100.20");
        Assertions.assertEquals(200, owner.getStatusCode().value());
    }

    private void signup(String loginId) {
        Map<String, String> body = Map.of(
                "loginId", loginId,
                "email", loginId + "@test.com",
                "password", PASSWORD,
                "name", "tester");
        ResponseEntity<String> response = restTemplate.postForEntity("/api/v1/members", body, String.class);
        Assertions.assertEquals(200, response.getStatusCode().value());
    }

    private ResponseEntity<String> login(String loginId, String password, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Forwarded-For", forwardedFor);
        Map<String, String> body = Map.of("loginId", loginId, "password", password);
        return restTemplate.postForEntity("/api/v1/members/login", new HttpEntity<>(body, headers), String.class);
    }
}
