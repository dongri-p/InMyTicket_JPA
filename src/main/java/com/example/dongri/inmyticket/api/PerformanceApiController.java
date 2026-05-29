package com.example.dongri.inmyticket.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.PerformanceDetailDto;
import com.example.dongri.inmyticket.api.dto.PerformanceListDto;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.service.PerformanceService;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PerformanceApiController {
    
    private final PerformanceService performanceService;

    // KOPIS 외부 API 공연데이터를 강제로 땡겨와 DB에 동기화 하는 API
    @PostMapping("/api/v1/performances/sync")
    public String syncPerformances() {
        log.info("API를 통한 공연데이터 동기화 요청 수신");
        performanceService.syncPerformances();
        return "KOPIS 공연 데이터 동기화 완료";
    }

    // 회원용 API: 전체 공연 목록 조회(v2 - dto 감싸기 구조로 확장성 확보)
    @GetMapping("/api/v1/performances")
    public Result performancesV2() {
        List<Performance> findPerformances = performanceService.findPerformances();

        // 엔티티 리스트를 안전하게 ListDto 리스트로 변환
        List<PerformanceListDto> collect = findPerformances.stream()
                .map(PerformanceListDto::new)
                .collect(Collectors.toList());
        
        // 오브젝트 내부 data 필드에 리스트를 넣어 JSON 
        return new Result(collect.size(), collect);
    }

    // 회원용 API: 특정 공연 상세 조회
    @GetMapping("/api/v1/performances/{id}")
    public PerformanceDetailDto performanceDetailDto(@PathVariable("id") Long id) {
        Performance performance = performanceService.findOne(id);
        return new PerformanceDetailDto(performance);
    }

    @Data
    @AllArgsConstructor
    static class Result<T> {
        private int count;
        private T data;
    }
}
