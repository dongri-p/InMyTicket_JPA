package com.example.dongri.inmyticket.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    private static final int MAX_PAGE_SIZE = 100;

    // 회원용 API: 공연 목록 페이지 조회(v2 - dto 감싸기 구조로 확장성 확보)
    // 비인증 공개 API이므로 페이지네이션 없이 전체 조회를 허용하면 대량조회로 인한 부하 위험이 있어 size를 제한
    @GetMapping("/api/v1/performances")
    public Result performancesV2(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Performance> findPerformances = performanceService.findPerformances(
                PageRequest.of(Math.max(page, 0), pageSize));

        // 엔티티 리스트를 안전하게 ListDto 리스트로 변환
        List<PerformanceListDto> collect = findPerformances.getContent().stream()
                .map(PerformanceListDto::new)
                .collect(Collectors.toList());

        // 오브젝트 내부 data 필드에 리스트를 넣어 JSON
        // count는 이 페이지에 포함된 항목 수, totalCount는 페이지네이션 이전(size 제한과 무관한) 전체 건수
        return new Result(collect.size(), findPerformances.getTotalElements(), collect);
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
        private long totalCount;
        private T data;
    }
}
