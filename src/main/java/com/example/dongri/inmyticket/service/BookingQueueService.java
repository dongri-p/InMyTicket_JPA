package com.example.dongri.inmyticket.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Service;

import com.example.dongri.inmyticket.api.dto.QueueStatusDto;
import com.example.dongri.inmyticket.domain.BusinessException;
import com.example.dongri.inmyticket.domain.ErrorCode;
import com.example.dongri.inmyticket.domain.Schedule;

import lombok.extern.slf4j.Slf4j;

// 예매 오픈 직후 몰리는 요청을 순번대로 흘려보내는 대기열 (Redis)
// - 회차의 예매 오픈 시각부터 window-minutes 동안만 운영. 그 밖의 시간엔 Redis를 쓰지 않음 (오픈 시각 없는 회차도)
// - 줄 서기: queue:{회차}:waiting (Sorted Set, 점수 = 줄 선 시각). 다시 요청해도 순번 유지(ZADD NX)
// - 입장: 1초마다 앞에서 admit-per-second명을 꺼내(ZPOPMIN) queue:{회차}:admitted:{회원} 키를 TTL로 줌
// - 예매 API는 좌석 락을 잡기 전에 이 키가 있는지만 봄
@Slf4j
@Service
public class BookingQueueService {

    private final StringRedisTemplate redis;
    private final Duration window;
    private final Duration admissionTtl;
    private final int admitPerSecond;

    // 이 서버가 입장을 돌려야 할 회차들. 줄 서기·순번 조회가 들어올 때 넣고, 줄이 비면 뺌.
    // 한가할 때 스케줄러가 Redis를 전혀 부르지 않게 하려고 둠 (서버가 재시작돼도 대기 화면이 순번을 다시 물어오면 채워짐)
    private final Set<Long> activeScheduleIds = ConcurrentHashMap.newKeySet();

    public BookingQueueService(StringRedisTemplate redis,
                               @Value("${booking-queue.window-minutes}") long windowMinutes,
                               @Value("${booking-queue.admission-ttl-minutes}") long admissionTtlMinutes,
                               @Value("${booking-queue.admit-per-second}") int admitPerSecond) {
        this.redis = redis;
        this.window = Duration.ofMinutes(windowMinutes);
        this.admissionTtl = Duration.ofMinutes(admissionTtlMinutes);
        this.admitPerSecond = admitPerSecond;
    }

    // 대기열 운영 중인 회차인지: 예매 오픈 시각 ~ 오픈 + window
    public boolean isQueueActive(Schedule schedule, LocalDateTime now) {
        LocalDateTime openAt = schedule.getBookingOpenAt();
        return openAt != null && !now.isBefore(openAt) && now.isBefore(openAt.plus(window));
    }

    // 줄 서기. 이미 서 있거나 입장한 회원이면 지금 상태를 그대로 돌려줌
    public QueueStatusDto join(Schedule schedule, Long memberId, LocalDateTime now) {
        if (!isQueueActive(schedule, now)) {
            return status(schedule, memberId, now);
        }
        Long scheduleId = schedule.getId();
        return withRedis(() -> {
            if (!Boolean.TRUE.equals(redis.hasKey(admittedKey(scheduleId, memberId)))) {
                redis.opsForZSet().addIfAbsent(waitingKey(scheduleId), memberId.toString(), System.currentTimeMillis());
            }
            activeScheduleIds.add(scheduleId);
            return queueStatus(scheduleId, memberId);
        });
    }

    // 내 순번 (대기 화면이 주기적으로 물어봄)
    public QueueStatusDto status(Schedule schedule, Long memberId, LocalDateTime now) {
        if (schedule.isBeforeBookingOpen(now)) {
            return QueueStatusDto.notOpen(schedule.getBookingOpenAt());
        }
        if (!isQueueActive(schedule, now)) {
            return QueueStatusDto.noQueue();
        }
        Long scheduleId = schedule.getId();
        return withRedis(() -> {
            activeScheduleIds.add(scheduleId);
            return queueStatus(scheduleId, memberId);
        });
    }

    // 예매 직전 확인: 대기열 운영 중인데 입장 허가가 없으면 좌석 락을 잡기 전에 거절
    public void assertAdmitted(Schedule schedule, Long memberId, LocalDateTime now) {
        if (!isQueueActive(schedule, now)) {
            return;
        }
        boolean admitted = withRedis(() -> Boolean.TRUE.equals(redis.hasKey(admittedKey(schedule.getId(), memberId))));
        if (!admitted) {
            throw new BusinessException(ErrorCode.QUEUE_ENTRY_REQUIRED);
        }
    }

    // 스케줄러가 1초마다 호출: 운영 중인 회차마다 앞에서 admit-per-second명 입장
    public void admitAll() {
        for (Long scheduleId : activeScheduleIds) {
            try {
                admitNext(scheduleId, admitPerSecond);
            } catch (DataAccessException e) {
                log.warn("대기열 입장 처리 실패. scheduleId={}, 원인={}", scheduleId, e.getMessage());
            }
        }
    }

    // 앞에서 count명을 꺼내 입장 허가. ZPOPMIN이라 서버가 여러 대여도 같은 사람을 두 번 꺼내지 않음
    int admitNext(Long scheduleId, int count) {
        Set<TypedTuple<String>> popped = redis.opsForZSet().popMin(waitingKey(scheduleId), count);
        if (popped == null || popped.isEmpty()) {
            activeScheduleIds.remove(scheduleId);
            return 0;
        }
        for (TypedTuple<String> member : popped) {
            redis.opsForValue().set(admittedKey(scheduleId, Long.valueOf(member.getValue())), "1", admissionTtl);
        }
        return popped.size();
    }

    private QueueStatusDto queueStatus(Long scheduleId, Long memberId) {
        Long ttlSeconds = redis.getExpire(admittedKey(scheduleId, memberId));
        if (ttlSeconds != null && ttlSeconds > 0) {
            return QueueStatusDto.admitted(ttlSeconds);
        }
        Long rank = redis.opsForZSet().rank(waitingKey(scheduleId), memberId.toString());
        if (rank == null) {
            // 줄에 없고 입장 허가도 없음 (아직 안 섰거나, 입장 시간이 지남) - 화면이 다시 줄을 세움
            return QueueStatusDto.notInQueue();
        }
        long position = rank + 1;
        return QueueStatusDto.waiting(position, (position + admitPerSecond - 1) / admitPerSecond);
    }

    // 대기열 운영 중 Redis가 응답하지 않으면 503 (대기열을 건너뛰고 예매를 열어 주지는 않음)
    private <T> T withRedis(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException e) {
            log.warn("대기열 저장소(Redis) 오류. 원인={}", e.getMessage());
            throw new BusinessException(ErrorCode.QUEUE_UNAVAILABLE, ErrorCode.QUEUE_UNAVAILABLE.getMessage(), e);
        }
    }

    private static String waitingKey(Long scheduleId) {
        return "queue:" + scheduleId + ":waiting";
    }

    private static String admittedKey(Long scheduleId, Long memberId) {
        return "queue:" + scheduleId + ":admitted:" + memberId;
    }
}
