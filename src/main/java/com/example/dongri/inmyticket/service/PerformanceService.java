package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.GenreGroup;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository.PerformanceScheduleCount;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final ScheduleRepository scheduleRepository;
    private final KopisService kopisService;
    private final PerformanceSyncService performanceSyncService;

    // 외부 API(KOPIS) 통신 중에는 DB 커넥션을 붙잡지 않도록 트랜잭션 밖에서 실행
    // (PaymentService의 "외부 통신 -> 짧은 DB 트랜잭션" 분리 패턴과 동일)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void syncPerformances() {

        // Kopis로부터 dto 리스트 땡겨오기
        List<KopisPerformanceResponse> kopisData = kopisService.fetchRecentPerformances();

        if (kopisData.isEmpty()) {
            log.info("동기화할 외부 데이터가 없습니다.");
            return;
        }

        log.info("가져온 외부 데이터 DB 동기화 시작");
        performanceSyncService.saveSyncedPerformances(kopisData);
        log.info("외부 데이터 DB 동기화 완료");
    }

    // 포스터가 비어 있는 기존 공연에 KOPIS 상세 조회로 포스터를 채워 넣음
    // syncPerformances()와 같은 이유로 KOPIS 호출(공연 수만큼)은 트랜잭션 밖에서 하고, 반영만 짧은 트랜잭션으로 처리
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int backfillPosterUrls() {

        List<Performance> targets = performanceRepository.findByPosterUrlIsNull();
        if (targets.isEmpty()) {
            return 0;
        }

        Map<Long, String> posterUrls = new HashMap<>();
        for (Performance performance : targets) {
            String posterUrl = kopisService.fetchPosterUrl(performance.getApiId());
            if (posterUrl != null) {
                posterUrls.put(performance.getId(), posterUrl);
            }
        }

        performanceSyncService.updatePosterUrls(posterUrls);
        log.info("공연 포스터 backfill 완료: 대상 {}건 중 {}건 반영", targets.size(), posterUrls.size());
        return posterUrls.size();
    }

    // DB에 저장된 공연 목록 페이지 조회 (비인증 공개 API라 페이지네이션 없이 전체 조회 시 대량조회 부하 위험이 있어 페이징 처리)
    // genre가 null이면 전체, 있으면 해당 장르 묶음만 조회
    // genre, keyword는 둘 다 선택 (keyword는 공백 제거 후 비어 있으면 검색 안 함)
    public Page<Performance> findPerformances(GenreGroup genre, String keyword, Pageable pageable) {
        String trimmed = keyword == null ? "" : keyword.strip();
        if (trimmed.isEmpty()) {
            return genre == null
                    ? performanceRepository.findAll(pageable)
                    : performanceRepository.findByCategoryIn(genre.getCategories(), pageable);
        }
        return genre == null
                ? performanceRepository.findByTitleContainingIgnoreCase(trimmed, pageable)
                : performanceRepository.findByCategoryInAndTitleContainingIgnoreCase(genre.getCategories(), trimmed, pageable);
    }

    // 목록 카드의 '예매중/오픈예정' 표시용: 공연 id -> 아직 시작 전인 회차 수 (회차 없는 공연은 맵에 없음)
    public Map<Long, Long> countUpcomingSchedules(List<Long> performanceIds) {
        if (performanceIds.isEmpty()) {
            return Map.of();
        }
        return scheduleRepository.countUpcomingSchedules(performanceIds, LocalDateTime.now()).stream()
                .collect(Collectors.toMap(PerformanceScheduleCount::getPerformanceId,
                                          PerformanceScheduleCount::getScheduleCount));
    }

    // 특정 공연 한편 상세 조회
    public Performance findOne(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연입니다. id=" + performanceId));
    }
    
}
