package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.repository.ScheduleRepository;
import com.example.dongri.inmyticket.service.PerformanceService;
import com.example.dongri.inmyticket.service.PerformanceSyncService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// KOPIS 실제 외부 API 호출은 테스트에서 제어 불가능하므로 KopisService만 목으로 대체하고,
// 나머지(PerformanceRepository)는 실제 DB로 검증한다
@SpringBootTest
public class PerformanceServiceTest {

    @Autowired private PerformanceRepository performanceRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    // 직접 new로 만들면 @Transactional 프록시가 없어 backfill의 변경 감지가 DB에 반영되지 않으므로 스프링 빈을 사용
    @Autowired private PerformanceSyncService performanceSyncService;

    private PerformanceService newPerformanceService(KopisService kopisService) {
        return new PerformanceService(performanceRepository, scheduleRepository, kopisService, performanceSyncService);
    }

    @Test
    @DisplayName("KOPIS 동기화 시 신규 공연은 저장되고, 이미 존재하는 apiId는 중복 저장되지 않는다")
    void syncPerformances_savesNewAndSkipsExisting() {
        // given
        String existingApiId = "kopis-existing-" + UUID.randomUUID();
        Performance existing = new Performance();
        existing.setApiId(existingApiId);
        existing.setTitle("이미 있는 공연");
        performanceRepository.save(existing);

        String newApiId = "kopis-new-" + UUID.randomUUID();
        KopisService mockKopisService = Mockito.mock(KopisService.class);
        Mockito.when(mockKopisService.fetchRecentPerformances())
                .thenReturn(List.of(
                        kopisResponse(existingApiId, "이미 있는 공연(외부에서 다시 옴)"),
                        kopisResponse(newApiId, "신규 공연")
                ));

        PerformanceService performanceService = newPerformanceService(mockKopisService);

        // when
        performanceService.syncPerformances();

        // then: 신규 apiId는 새로 저장되고, 기존 apiId는 중복 저장되지 않는다
        Assertions.assertTrue(performanceRepository.findApiIdsIn(List.of(newApiId)).contains(newApiId));
        long existingCount = performanceRepository.findAll().stream()
                .filter(p -> existingApiId.equals(p.getApiId()))
                .count();
        Assertions.assertEquals(1, existingCount);
    }

    @Test
    @DisplayName("KOPIS 응답 배치 안에 같은 mt20id가 중복으로 들어와도 한 건만 저장된다")
    void syncPerformances_withDuplicateApiIdInSameBatch_savesOnlyOnce() {
        // given
        String duplicateApiId = "kopis-batch-dup-" + UUID.randomUUID();
        KopisService mockKopisService = Mockito.mock(KopisService.class);
        Mockito.when(mockKopisService.fetchRecentPerformances())
                .thenReturn(List.of(
                        kopisResponse(duplicateApiId, "같은 공연 첫 번째"),
                        kopisResponse(duplicateApiId, "같은 공연 두 번째(중복)")
                ));

        PerformanceService performanceService = newPerformanceService(mockKopisService);

        // when
        performanceService.syncPerformances();

        // then
        long count = performanceRepository.findAll().stream()
                .filter(p -> duplicateApiId.equals(p.getApiId()))
                .count();
        Assertions.assertEquals(1, count);
    }

    @Test
    @DisplayName("KOPIS 동기화 시 공연 기간과 공연장명이 함께 저장되고, 형식이 다른 날짜는 비워 둔다")
    void syncPerformances_savesPeriodAndVenue() {
        // given
        String apiId = "kopis-period-" + UUID.randomUUID();
        String badDateApiId = "kopis-bad-date-" + UUID.randomUUID();
        KopisPerformanceResponse withPeriod = kopisResponse(apiId, "기간 있는 공연");
        withPeriod.setPrfpdfrom("2026.11.07");
        withPeriod.setPrfpdto("2026.11.14");
        KopisPerformanceResponse badDate = kopisResponse(badDateApiId, "날짜 형식이 다른 공연");
        badDate.setPrfpdfrom("2026-11-07");
        badDate.setPrfpdto("");

        KopisService mockKopisService = Mockito.mock(KopisService.class);
        Mockito.when(mockKopisService.fetchRecentPerformances()).thenReturn(List.of(withPeriod, badDate));

        // when
        newPerformanceService(mockKopisService).syncPerformances();

        // then
        Performance saved = findByApiId(apiId);
        Assertions.assertEquals(LocalDate.of(2026, 11, 7), saved.getStartDate());
        Assertions.assertEquals(LocalDate.of(2026, 11, 14), saved.getEndDate());
        Assertions.assertEquals("테스트홀", saved.getVenueName());
        Assertions.assertEquals("서울특별시", saved.getArea());

        Performance savedBadDate = findByApiId(badDateApiId);
        Assertions.assertNull(savedBadDate.getStartDate());
        Assertions.assertNull(savedBadDate.getEndDate());
    }

    @Test
    @DisplayName("기간 컬럼 추가 전에 저장된 공연은 KOPIS 상세 조회로 비어 있는 값만 채워진다")
    void backfillMissingDetails_fillsOnlyEmptyFields() {
        // given: 포스터는 있고 기간·공연장명은 비어 있는 기존 공연
        String apiId = "kopis-backfill-" + UUID.randomUUID();
        Performance existing = new Performance();
        existing.setApiId(apiId);
        existing.setTitle("기간 없는 기존 공연");
        existing.setPosterUrl("https://example.com/original.jpg");
        performanceRepository.save(existing);

        KopisPerformanceResponse detail = kopisResponse(apiId, "기간 없는 기존 공연");
        detail.setPoster("http://example.com/other.jpg");
        detail.setPrfpdfrom("2026.11.07");
        detail.setPrfpdto("2026.11.14");

        KopisService mockKopisService = Mockito.mock(KopisService.class);
        Mockito.when(mockKopisService.fetchDetail(Mockito.anyString())).thenReturn(null);
        Mockito.when(mockKopisService.fetchDetail(apiId)).thenReturn(detail);

        // when
        newPerformanceService(mockKopisService).backfillMissingDetails();

        // then: 비어 있던 기간·공연장명만 채워지고, 이미 있던 포스터는 그대로
        Performance updated = performanceRepository.findById(existing.getId()).orElseThrow();
        Assertions.assertEquals(LocalDate.of(2026, 11, 7), updated.getStartDate());
        Assertions.assertEquals(LocalDate.of(2026, 11, 14), updated.getEndDate());
        Assertions.assertEquals("테스트홀", updated.getVenueName());
        Assertions.assertEquals("https://example.com/original.jpg", updated.getPosterUrl());
        Assertions.assertEquals("서울특별시", updated.getArea());
    }

    @Test
    @DisplayName("포스터·기간은 있고 지역만 비어 있는 공연(V8 이전 저장)도 backfill 대상이 된다")
    void backfillMissingDetails_fillsAreaOnly() {
        // given: V7까지의 값은 다 있고 지역만 없는 기존 공연
        String apiId = "kopis-backfill-area-" + UUID.randomUUID();
        Performance existing = new Performance();
        existing.setApiId(apiId);
        existing.setTitle("지역 없는 기존 공연");
        existing.setPosterUrl("https://example.com/original.jpg");
        existing.setStartDate(LocalDate.of(2026, 11, 7));
        existing.setEndDate(LocalDate.of(2026, 11, 14));
        existing.setVenueName("기존홀");
        performanceRepository.save(existing);

        KopisPerformanceResponse detail = kopisResponse(apiId, "지역 없는 기존 공연");
        detail.setArea("부산광역시");

        KopisService mockKopisService = Mockito.mock(KopisService.class);
        Mockito.when(mockKopisService.fetchDetail(Mockito.anyString())).thenReturn(null);
        Mockito.when(mockKopisService.fetchDetail(apiId)).thenReturn(detail);

        // when
        newPerformanceService(mockKopisService).backfillMissingDetails();

        // then
        Performance updated = performanceRepository.findById(existing.getId()).orElseThrow();
        Assertions.assertEquals("부산광역시", updated.getArea());
        Assertions.assertEquals("기존홀", updated.getVenueName());
    }

    @Test
    @DisplayName("findOne()은 존재하지 않는 id에 대해 예외를 던진다")
    void findOne_withNonExistentId_throwsIllegalArgument() {
        PerformanceService performanceService = newPerformanceService(Mockito.mock(KopisService.class));

        Assertions.assertThrows(IllegalArgumentException.class, () -> performanceService.findOne(999999L));
    }

    @Test
    @DisplayName("findPerformances()는 요청한 페이지 크기대로 결과를 반환한다")
    void findPerformances_returnsRequestedPage() {
        String prefix = "page-svc-test-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        for (int i = 0; i < 3; i++) {
            Performance performance = new Performance();
            performance.setApiId(prefix + i);
            performance.setTitle(prefix + i);
            performanceRepository.save(performance);
        }

        PerformanceService performanceService = newPerformanceService(Mockito.mock(KopisService.class));

        Page<Performance> page = performanceService.findPerformances(null, null, null, null, PageRequest.of(0, 2));

        Assertions.assertEquals(2, page.getContent().size());
        Assertions.assertTrue(page.getTotalElements() >= 3);
    }

    private Performance findByApiId(String apiId) {
        return performanceRepository.findAll().stream()
                .filter(p -> apiId.equals(p.getApiId()))
                .findFirst()
                .orElseThrow();
    }

    private KopisPerformanceResponse kopisResponse(String apiId, String title) {
        KopisPerformanceResponse response = new KopisPerformanceResponse();
        response.setMt20id(apiId);
        response.setPrfnm(title);
        response.setGenrenm("연극");
        response.setPrfstate("공연중");
        response.setFcltynm("테스트홀");
        response.setArea("서울특별시");
        return response;
    }
}
