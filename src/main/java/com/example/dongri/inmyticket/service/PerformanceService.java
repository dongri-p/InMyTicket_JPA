package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
import com.example.dongri.inmyticket.repository.PerformanceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PerformanceService {

    private final PerformanceRepository performanceRepository;
    private final KopisService kopisService;

    @Transactional
    public void syncPerformances() {

        // Kopis로부터 dto 리스트 땡겨오기
        List<KopisPerformanceResponse> kopisData = kopisService.fetchRecentPerformances();

        if (kopisData.isEmpty()) {
            log.info("동기화할 외부 데이터가 없습니다.");
            return;
        }

        log.info("가져온 외부 데이터 DB 동기화 시작");

        // 이미 DB에 들어있는 공연들의 apiId를 한 번에 조회 (아이템별 개별 조회로 인한 N+1 방지)
        List<String> apiIds = kopisData.stream()
                .map(KopisPerformanceResponse::getMt20id)
                .collect(Collectors.toList());
        Set<String> existingApiIds = Set.copyOf(performanceRepository.findApiIdsIn(apiIds));

        for(KopisPerformanceResponse dto : kopisData) {

            // 이미 DB에 들어있는 공연인지 체크
            if(existingApiIds.contains(dto.getMt20id())) {
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

        log.info("외부 데이터 DB 동기화 완료");
    }

    // DB에 저장된 공연 목록 페이지 조회 (비인증 공개 API라 페이지네이션 없이 전체 조회 시 대량조회 부하 위험이 있어 페이징 처리)
    public Page<Performance> findPerformances(Pageable pageable) {
        return performanceRepository.findAll(pageable);
    }

    // 특정 공연 한편 상세 조회
    public Performance findOne(Long performanceId) {
        return performanceRepository.findById(performanceId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 공연입니다. id=" + performanceId));
    }
    
}
