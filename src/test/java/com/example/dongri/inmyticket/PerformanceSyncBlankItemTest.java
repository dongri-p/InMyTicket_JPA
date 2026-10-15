package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;
import com.example.dongri.inmyticket.service.PerformanceSyncService;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

// apiId나 제목이 빈 KOPIS 항목(에러 응답 등)은 빈 공연으로 저장하지 않음
@SpringBootTest
public class PerformanceSyncBlankItemTest {

    @Autowired private PerformanceSyncService performanceSyncService;
    @Autowired private PerformanceRepository performanceRepository;

    @Test
    @DisplayName("apiId나 제목이 없는 항목은 건너뛰고 정상 항목만 저장한다")
    void saveSyncedPerformances_skipsBlankItems() {
        // given
        String validApiId = "PF-" + UUID.randomUUID().toString().substring(0, 8);
        KopisPerformanceResponse valid = item(validApiId, "정상 공연");
        KopisPerformanceResponse noApiId = item(null, null);
        KopisPerformanceResponse blankApiId = item(" ", "제목만 있는 항목");
        KopisPerformanceResponse noTitle = item("PF-" + UUID.randomUUID().toString().substring(0, 8), "");
        long blankBefore = countBlankPerformances();

        // when
        performanceSyncService.saveSyncedPerformances(List.of(noApiId, blankApiId, noTitle, valid));

        // then
        Assertions.assertEquals(blankBefore, countBlankPerformances());
        Assertions.assertEquals(List.of(validApiId), performanceRepository.findApiIdsIn(List.of(validApiId)));
        Assertions.assertTrue(performanceRepository.findApiIdsIn(List.of(noTitle.getMt20id())).isEmpty());
    }

    private long countBlankPerformances() {
        return performanceRepository.findAll().stream()
                .map(Performance::getApiId)
                .filter(apiId -> apiId == null || apiId.isBlank())
                .count();
    }

    private KopisPerformanceResponse item(String mt20id, String prfnm) {
        KopisPerformanceResponse dto = new KopisPerformanceResponse();
        dto.setMt20id(mt20id);
        dto.setPrfnm(prfnm);
        return dto;
    }
}
