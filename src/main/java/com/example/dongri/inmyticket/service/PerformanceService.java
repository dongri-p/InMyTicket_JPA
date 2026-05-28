package com.example.dongri.inmyticket.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceListResponse;
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

        log.info("가져온 외부 데이터 DB 동기화 시작");

        for(KopisPerformanceResponse dto : kopisData) {

            // 이미 DB에 들어있는 공연인지 apiId로 체크
            if(performanceRepository.findByApiId(dto.getMt20id()).isPresent()) {
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
    
}
