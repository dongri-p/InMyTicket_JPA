package com.example.dongri.inmyticket.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// 무차별 대입(brute-force) 로그인 공격을 막기 위한 loginId 단위 실패 횟수 추적.
// 단일 인스턴스 전제의 인메모리 구현(재시작 시 초기화, 다중 인스턴스 스케일아웃 시 공유 안 됨) -
// 학습용 프로젝트 규모에서는 충분하지만, 실제 운영 환경이라면 Redis 등 공유 저장소로 교체 필요.
@Component
public class LoginAttemptGuard {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);

    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();

    public void checkNotLocked(String loginId) {
        Attempt attempt = attempts.get(loginId);
        if (attempt != null
                && attempt.count >= MAX_ATTEMPTS
                && Duration.between(attempt.lastFailureAt, LocalDateTime.now()).compareTo(LOCKOUT_DURATION) < 0) {
            throw new IllegalStateException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    public void recordFailure(String loginId) {
        attempts.compute(loginId, (id, attempt) -> {
            LocalDateTime now = LocalDateTime.now();
            if (attempt == null || Duration.between(attempt.lastFailureAt, now).compareTo(LOCKOUT_DURATION) >= 0) {
                return new Attempt(1, now);
            }
            return new Attempt(attempt.count + 1, now);
        });
    }

    public void recordSuccess(String loginId) {
        attempts.remove(loginId);
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
