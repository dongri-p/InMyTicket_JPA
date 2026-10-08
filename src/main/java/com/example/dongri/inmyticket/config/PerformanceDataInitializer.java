package com.example.dongri.inmyticket.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.service.PerformanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 로컬은 ddl-auto: create라 재시작마다 공연 데이터가 사라지므로, 기동 시 자동으로 KOPIS 동기화를 1회 시도한다.
// 이미 데이터가 있으면 스킵 — prod 재배포마다 불필요하게 KOPIS를 호출하지 않기 위함(AdminAccountInitializer와 동일한 idempotent 패턴)
// LocalTestDataInitializer가 이 동기화 결과(performanceId)를 참조하므로 먼저 실행되도록 순서를 명시
@Slf4j
@Component
@RequiredArgsConstructor
@Order(1)
public class PerformanceDataInitializer implements ApplicationRunner {

    private final PerformanceRepository performanceRepository;
    private final PerformanceService performanceService;

    @Override
    public void run(ApplicationArguments args) {
        if (performanceRepository.count() > 0) {
            // 이미 데이터가 있는 운영 DB는 sync가 기존 공연을 건너뛰므로, 포스터 컬럼(V6) 이전 데이터를 여기서 채움.
            // 비어 있는 공연만 대상이라 한 번 채워지면 이후 재배포 때는 KOPIS를 호출하지 않음
            try {
                performanceService.backfillPosterUrls();
            } catch (RuntimeException e) {
                log.warn("기동 시 공연 포스터 backfill 실패 — 다음 기동 때 다시 시도됨", e);
            }
            return;
        }

        try {
            performanceService.syncPerformances();
            log.info("기동 시 KOPIS 공연 데이터 자동 동기화 완료");
        } catch (RuntimeException e) {
            log.warn("기동 시 KOPIS 공연 데이터 자동 동기화 실패 — 필요시 POST /api/v1/performances/sync로 수동 재시도 가능", e);
        }
    }
}
