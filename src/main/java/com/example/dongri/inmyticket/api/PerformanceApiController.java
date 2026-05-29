package com.example.dongri.inmyticket.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.service.PerformanceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PerformanceApiController {
    
    private final PerformanceService performanceService;

    @PostMapping("/api/v1/performances/sync")
    public String syncPerformances() {

        log.info("API를 통한 공연데이터 동기화 요청 수신");

        performanceService.syncPerformances();

        return "KOPIS 공연 데이터 동기화 완료";
    }
}
