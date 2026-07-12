package com.example.dongri.inmyticket.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 무차별 대입(brute-force) 로그인 공격을 막기 위한 실패 횟수 추적.
// 키는 (loginId, 클라이언트 IP) 조합 - loginId만으로 키를 잡으면 공격자가 임의의 loginId를
// 알아내 틀린 비밀번호를 5회만 보내는 것으로 그 계정을 잠글 수 있는(타인 계정 lockout DoS)
// 문제가 있어, 공격자의 IP와 무관한 정상 사용자의 로그인까지 막히지 않도록 IP를 키에 포함.
// 단일 인스턴스 전제의 인메모리 구현(재시작 시 초기화, 다중 인스턴스 스케일아웃 시 공유 안 됨) -
// 학습용 프로젝트 규모에서는 충분하지만, 실제 운영 환경이라면 Redis 등 공유 저장소로 교체 필요.
@Component
public class LoginAttemptGuard {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void checkNotLocked(String loginId, String clientIp) {
        Attempt attempt = attempts.get(key(loginId, clientIp));
        if (attempt != null
                && attempt.count >= MAX_ATTEMPTS
                && Duration.between(attempt.lastFailureAt, LocalDateTime.now()).compareTo(LOCKOUT_DURATION) < 0) {
            throw new IllegalStateException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public void recordFailure(String loginId, String clientIp) {
        attempts.compute(key(loginId, clientIp), (id, attempt) -> {
            LocalDateTime now = LocalDateTime.now();
            if (attempt == null || Duration.between(attempt.lastFailureAt, now).compareTo(LOCKOUT_DURATION) >= 0) {
                return new Attempt(1, now);
            }
            return new Attempt(attempt.count + 1, now);
        });
    }

    public void recordSuccess(String loginId, String clientIp) {
        attempts.remove(key(loginId, clientIp));
    }

    private String key(String loginId, String clientIp) {
        return loginId + "|" + clientIp;
    }

    // 성공으로만 제거되던 attempts 맵이, 존재하지 않는 loginId로 실패를 반복 유발하면 무한정 커질 수 있어
    // 잠금 기간이 지난(더 이상 잠금에 영향 없는) 기록을 주기적으로 청소함
    @Scheduled(fixedDelay = 600_000)
    public void evictExpiredAttempts() {
        evictAttemptsOlderThan(LocalDateTime.now().minus(LOCKOUT_DURATION));
    }

    void evictAttemptsOlderThan(LocalDateTime cutoff) {
        attempts.entrySet().removeIf(entry -> entry.getValue().lastFailureAt.isBefore(cutoff));
    }

    int trackedLoginIdCount() {
        return attempts.size();
    }

    private static class Attempt {
        final int count;
        final LocalDateTime lastFailureAt;

        Attempt(int count, LocalDateTime lastFailureAt) {
            this.count = count;
            this.lastFailureAt = lastFailureAt;
        }
    }
}
