package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.GenreGroup;
import com.example.dongri.inmyticket.domain.RegionGroup;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.PerformanceCursor;
import com.example.dongri.inmyticket.domain.ResourceNotFoundException;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository.PerformanceScheduleCount;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PerformanceService {

    private static final int BACKFILL_RETRY_ROUNDS = 2;
    private static final long BACKFILL_RETRY_PAUSE_MS = 3000;

    // 재시도 전 기본 대기 시간. 테스트에서 실제로 기다리지 않도록 바꿀 수 있게 열어 둠
    @Setter
    private long backfillRetryPauseMs = BACKFILL_RETRY_PAUSE_MS;

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

    // 포스터·공연 기간·지역이 비어 있는 기존 공연에 KOPIS 상세 조회로 빈 값을 채워 넣음
    // syncPerformances()와 같은 이유로 KOPIS 호출(공연 수만큼)은 트랜잭션 밖에서 하고, 반영만 짧은 트랜잭션으로 처리
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int backfillMissingDetails() {

        List<Performance> targets = performanceRepository.findByPosterUrlIsNullOrStartDateIsNullOrAreaIsNull();
        if (targets.isEmpty()) {
            return 0;
        }

        // 운영(EC2)에서는 호출 간격을 둬도 일부(100건 중 13건)가 실패했음 -> 실패한 공연만 모아 잠깐 쉬었다가 다시 조회.
        // 그래도 실패한 공연은 비어 있는 채로 남고 다음 기동 때 다시 시도됨
        Map<Long, KopisPerformanceResponse> details = new HashMap<>();
        List<Performance> pending = targets;
        for (int round = 0; round <= BACKFILL_RETRY_ROUNDS && !pending.isEmpty(); round++) {
            if (round > 0 && !pauseBeforeRetry(round, pending.size())) {
                break;
            }
            List<Performance> failed = new ArrayList<>();
            for (Performance performance : pending) {
                KopisPerformanceResponse detail = kopisService.fetchDetail(performance.getApiId());
                if (detail != null) {
                    details.put(performance.getId(), detail);
                } else {
                    failed.add(performance);
                }
            }
            pending = failed;
        }

        performanceSyncService.updateMissingDetails(details);
        log.info("공연 상세 정보 backfill 완료: 대상 {}건 중 {}건 조회 성공", targets.size(), details.size());
        return details.size();
    }

    // 재시도 전 대기 (라운드마다 길게: 3초, 6초). 인터럽트되면 재시도를 그만둠
    private boolean pauseBeforeRetry(int round, int failedCount) {
        log.info("공연 상세 조회 실패 {}건 재시도 ({}/{})", failedCount, round, BACKFILL_RETRY_ROUNDS);
        try {
            Thread.sleep(backfillRetryPauseMs * round);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // DB에 저장된 공연 목록 조회 (비인증 공개 API라 한 번에 limit건까지만)
    // genre, region, keyword, status는 모두 선택 (keyword는 공백 제거 후 비어 있으면 검색 안 함, status가 null이면 상태 무관)
    // cursor가 null이면 첫 페이지, 있으면 그 공연 다음부터. 정렬은 쿼리에 고정(예매중 -> 오픈예정 -> 공연종료, 같은 그룹은 id 순)
    public List<Performance> findPerformances(GenreGroup genre, RegionGroup region, String keyword, BookingStatus status,
                                              PerformanceCursor cursor, int limit) {
        ListFilter f = ListFilter.of(genre, region, keyword, status);
        LocalDateTime now = LocalDateTime.now();
        return performanceRepository.findForListAfter(f.allGenres(), f.categories(), f.allRegions(), f.areas(), f.keyword(),
                f.status(), cursor == null ? -1 : cursor.bookingGroup(), cursor == null ? 0L : cursor.id(),
                now, now.toLocalDate(), PageRequest.of(0, limit));
    }

    // 같은 조건의 전체 공연 수 (목록 제목 옆 건수 표시용)
    public long countPerformances(GenreGroup genre, RegionGroup region, String keyword, BookingStatus status) {
        ListFilter f = ListFilter.of(genre, region, keyword, status);
        LocalDateTime now = LocalDateTime.now();
        return performanceRepository.countForList(f.allGenres(), f.categories(), f.allRegions(), f.areas(), f.keyword(),
                f.status(), now, now.toLocalDate());
    }

    // 목록 필터를 쿼리 파라미터 형태로 바꾼 값 (목록 조회와 건수 조회가 같은 조건을 쓰도록)
    // genre/region이 없으면 in 절이 무시되지만, 빈 리스트를 넘기면 'in ()'가 되는 DB가 있어 아무 값이나 하나 넣어 둠
    private record ListFilter(boolean allGenres, List<String> categories, boolean allRegions, List<String> areas,
                              String keyword, int status) {
        static ListFilter of(GenreGroup genre, RegionGroup region, String keyword, BookingStatus status) {
            String trimmed = keyword == null ? "" : keyword.strip();
            return new ListFilter(genre == null, genre == null ? List.of("") : genre.getCategories(),
                    region == null, region == null ? List.of("") : region.getAreas(),
                    trimmed.isEmpty() ? null : escapeLike(trimmed),
                    status == null ? -1 : status.getCode());
        }
    }

    // 검색어의 %, _를 와일드카드가 아닌 글자로 검색하도록 쿼리의 escape 문자(!)를 앞에 붙임
    private static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
                .orElseThrow(() -> new ResourceNotFoundException("존재하지 않는 공연입니다. id=" + performanceId));
    }
    
}
