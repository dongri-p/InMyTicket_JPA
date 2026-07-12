package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;

import lombok.RequiredArgsConstructor;

// PerformanceService.syncPerformances()가 외부 API(KOPIS) 통신을 트랜잭션 밖에서 마친 뒤 호출하는
// 짧은 DB 트랜잭션 전용 서비스 (PaymentService/PaymentApprovalService와 동일한 분리 패턴)
@Service
@Transactional
@RequiredArgsConstructor
public class PerformanceSyncService {

    private final PerformanceRepository performanceRepository;

    public void saveSyncedPerformances(List<KopisPerformanceResponse> kopisData) {

        // 이미 DB에 들어있는 공연들의 apiId를 한 번에 조회 (아이템별 개별 조회로 인한 N+1 방지)
        List<String> apiIds = kopisData.stream()
                .map(KopisPerformanceResponse::getMt20id)
                .collect(Collectors.toList());
        // 이번 배치 안에서 새로 저장하는 apiId도 즉시 반영해야 하므로 가변 Set 사용
        // (배치 응답 안에 같은 mt20id가 중복으로 들어와도 두 번 저장되지 않게 함)
        Set<String> seenApiIds = new HashSet<>(performanceRepository.findApiIdsIn(apiIds));

        for (KopisPerformanceResponse dto : kopisData) {

            // 이미 DB에 들어있거나, 이번 배치에서 이미 처리한 공연인지 체크
            if (!seenApiIds.add(dto.getMt20id())) {
                continue;
            }

            Performance performance = new Performance();
            performance.setApiId(dto.getMt20id());
            performance.setTitle(dto.getPrfnm());
            performance.setCategory(dto.getGenrenm());
            performance.setStatus(dto.getPrfstate());
            performance.setLastUpdatedAt(LocalDateTime.now());

            performance.setArtist("KOPIS 연동 아티스트");
            performance.setDescription(dto.getPrfnm() + " 공연입니다. 공연장: " + dto.getFcltynm());

            performanceRepository.save(performance);
        }
    }
}
