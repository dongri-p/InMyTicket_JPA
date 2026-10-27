package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.GenreGroup;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.service.PerformanceService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

// 목록 전체 건수 캐시: 검색어 없는 조합은 유지 시간 동안 같은 값, 검색어가 있으면 항상 DB에서 셈
@SpringBootTest(properties = "performance.list-count-cache-ttl-ms=600000")
public class PerformanceCountCacheTest {

    @Autowired private PerformanceService performanceService;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("검색어 없는 건수는 캐시 유지 시간 동안 새 공연이 생겨도 그대로다")
    void countWithoutKeyword_isCached() {
        long before = performanceService.countPerformances(GenreGroup.CLASSIC, null, null, null);

        save("cache-" + UUID.randomUUID(), "서양음악(클래식)");

        Assertions.assertEquals(before, performanceService.countPerformances(GenreGroup.CLASSIC, null, null, null));
    }

    @Test
    @DisplayName("검색어가 있으면 캐시하지 않아 방금 저장한 공연도 바로 센다")
    void countWithKeyword_isNotCached() {
        String tag = "cache-kw-" + UUID.randomUUID().toString().substring(0, 8);
        save(tag + "-1", "뮤지컬");
        Assertions.assertEquals(1, performanceService.countPerformances(null, null, tag, null));

        save(tag + "-2", "뮤지컬");
        Assertions.assertEquals(2, performanceService.countPerformances(null, null, tag, null));
    }

    private void save(String title, String category) {
        Performance performance = new Performance();
        performance.setApiId(title);
        performance.setTitle(title);
        performance.setCategory(category);
        performanceRepository.save(performance);
    }
}
