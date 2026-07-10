package com.example.dongri.inmyticket.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

// evictAttemptsOlderThan/trackedLoginIdCount가 package-private이라 service 패키지에 위치.
// Spring 컨텍스트 없이도 검증 가능한 순수 단위 테스트
public class LoginAttemptGuardTest {

    @Test
    @DisplayName("잠금 기간이 지난 실패 기록은 청소 대상이고, 최근 기록은 유지된다")
    void evictAttemptsOlderThan_removesOnlyStaleEntries() {
        // given
        LoginAttemptGuard guard = new LoginAttemptGuard();
        guard.recordFailure("stale-user");
        guard.recordFailure("fresh-user");
        Assertions.assertEquals(2, guard.trackedLoginIdCount());

        // when: 미래 시각을 cutoff로 주면 방금 기록한 stale-user/fresh-user 모두 그 이전이라 청소 대상이 되지만,
        // 과거 시각을 cutoff로 주면 방금 기록한 항목은 살아남는다
        guard.evictAttemptsOlderThan(LocalDateTime.now().minusMinutes(10));
        Assertions.assertEquals(2, guard.trackedLoginIdCount(), "cutoff 이전 기록은 아직 없으므로 그대로 유지되어야 함");

        guard.evictAttemptsOlderThan(LocalDateTime.now().plusMinutes(1));
        Assertions.assertEquals(0, guard.trackedLoginIdCount(), "cutoff 이후 기록은 모두 청소되어야 함");
    }

    @Test
    @DisplayName("존재하지 않는 여러 아이디로 실패를 반복해도, 청소 후에는 추적 대상이 남지 않는다")
    void repeatedFailuresAcrossManyIds_doNotAccumulateForever() {
        // given
        LoginAttemptGuard guard = new LoginAttemptGuard();
        for (int i = 0; i < 100; i++) {
            guard.recordFailure("non-existent-user-" + i);
        }
        Assertions.assertEquals(100, guard.trackedLoginIdCount());

        // when: 잠금 기간이 지난 시점 기준으로 청소
        guard.evictAttemptsOlderThan(LocalDateTime.now().plusMinutes(1));

        // then
        Assertions.assertEquals(0, guard.trackedLoginIdCount());
    }
}
