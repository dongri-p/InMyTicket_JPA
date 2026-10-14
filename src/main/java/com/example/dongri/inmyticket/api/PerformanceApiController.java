package com.example.dongri.inmyticket.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.ListResult;
import com.example.dongri.inmyticket.api.dto.PerformanceDetailDto;
import com.example.dongri.inmyticket.api.dto.PerformanceListDto;
import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.GenreGroup;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.service.PerformanceService;

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
    public ListResult<List<PerformanceListDto>> performancesV2(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            // 장르 메뉴 필터 (예: ?genre=CONCERT). 없으면 전체, 잘못된 값이면 400
            @RequestParam(required = false) GenreGroup genre,
            // 헤더 검색창: 제목 부분 검색 (예: ?keyword=레미제라블). genre와 같이 쓰면 둘 다 만족하는 공연만
            @RequestParam(required = false) String keyword,
            // 예매 상태 필터 (ON_SALE 예매중 / UPCOMING 오픈예정 / ENDED 공연종료). 없으면 전체, 잘못된 값이면 400
            @RequestParam(required = false) BookingStatus status) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // 정렬은 서비스 쿼리에 고정: 예매중 공연이 첫 화면에 오도록 예매중 -> 오픈예정 -> 공연종료 순,
        // 같은 그룹 안에서는 id 순 (정렬 없이 페이징하면 '더 보기' 시 중복·누락이 생길 수 있음)
        Page<Performance> findPerformances = performanceService.findPerformances(
                genre, keyword, status, PageRequest.of(Math.max(page, 0), pageSize));

        // 이 페이지 공연들의 회차 수를 한 번에 조회 (공연별 개별 조회 시 N+1)
        List<Long> performanceIds = findPerformances.getContent().stream()
                .map(Performance::getId)
                .collect(Collectors.toList());
        Map<Long, Long> scheduleCounts = performanceService.countUpcomingSchedules(performanceIds);

        // 엔티티 리스트를 안전하게 ListDto 리스트로 변환
        List<PerformanceListDto> collect = findPerformances.getContent().stream()
                .map(p -> new PerformanceListDto(p, scheduleCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());

        // count는 이 페이지에 포함된 항목 수, totalCount는 페이지네이션 이전(size 제한과 무관한) 전체 건수
        return new ListResult<>(collect.size(), findPerformances.getTotalElements(), collect);
    }

    // 회원용 API: 특정 공연 상세 조회
    @GetMapping("/api/v1/performances/{id}")
    public PerformanceDetailDto performanceDetailDto(@PathVariable("id") Long id) {
        Performance performance = performanceService.findOne(id);
        return new PerformanceDetailDto(performance);
    }
}
