package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.service.PerformanceService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

// KOPIS 실제 외부 API 호출은 테스트에서 제어 불가능하므로 KopisService만 목으로 대체하고,
// 나머지(PerformanceRepository)는 실제 DB로 검증한다
@SpringBootTest
public class PerformanceServiceTest {

    @Autowired private PerformanceRepository performanceRepository;

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

        PerformanceService performanceService = new PerformanceService(performanceRepository, mockKopisService);

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
    @DisplayName("findOne()은 존재하지 않는 id에 대해 예외를 던진다")
    void findOne_withNonExistentId_throwsIllegalArgument() {
        PerformanceService performanceService = new PerformanceService(performanceRepository, Mockito.mock(KopisService.class));

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

        PerformanceService performanceService = new PerformanceService(performanceRepository, Mockito.mock(KopisService.class));

        Page<Performance> page = performanceService.findPerformances(PageRequest.of(0, 2));

        Assertions.assertEquals(2, page.getContent().size());
        Assertions.assertTrue(page.getTotalElements() >= 3);
    }

    private KopisPerformanceResponse kopisResponse(String apiId, String title) {
        KopisPerformanceResponse response = new KopisPerformanceResponse();
        response.setMt20id(apiId);
        response.setPrfnm(title);
        response.setGenrenm("연극");
        response.setPrfstate("공연중");
        response.setFcltynm("테스트홀");
        return response;
    }
}
