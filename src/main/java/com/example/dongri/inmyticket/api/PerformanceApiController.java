package com.example.dongri.inmyticket.api;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.dongri.inmyticket.api.dto.ListResult;
import com.example.dongri.inmyticket.api.dto.PerformanceDetailDto;
import com.example.dongri.inmyticket.api.dto.PerformanceListDto;
import com.example.dongri.inmyticket.domain.BookingStatus;
import com.example.dongri.inmyticket.domain.GenreGroup;
import com.example.dongri.inmyticket.domain.RegionGroup;
import com.example.dongri.inmyticket.domain.Performance;
import com.example.dongri.inmyticket.domain.PerformanceCursor;
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
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void syncPerformances() {
        log.info("API를 통한 공연데이터 동기화 요청 수신");
        performanceService.syncPerformances();
    }

    private static final int MAX_PAGE_SIZE = 100;

    // 회원용 API: 공연 목록 조회 ('더 보기' 커서 페이지네이션)
    // 비인증 공개 API이므로 한 번에 가져가는 양(size)을 제한
    @GetMapping("/api/v1/performances")
    public ListResult<List<PerformanceListDto>> performancesV2(
            // 직전 응답의 nextCursor. 없으면 첫 페이지, 형식이 틀리면 400
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size,
            // 장르 메뉴 필터 (예: ?genre=CONCERT). 없으면 전체, 잘못된 값이면 400
            @RequestParam(required = false) GenreGroup genre,
            // 지역 필터 (예: ?region=SEOUL). 없으면 전체, 잘못된 값이면 400
            @RequestParam(required = false) RegionGroup region,
            // 헤더 검색창: 제목 부분 검색 (예: ?keyword=레미제라블). genre와 같이 쓰면 둘 다 만족하는 공연만
            @RequestParam(required = false) String keyword,
            // 예매 상태 필터 (ON_SALE 예매중 / UPCOMING 오픈예정 / ENDED 공연종료). 없으면 전체, 잘못된 값이면 400
            @RequestParam(required = false) BookingStatus status) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PerformanceCursor after = cursor == null ? null : PerformanceCursor.decode(cursor);

        // 한 건 더 읽어서 다음 페이지가 있는지 판단 (전체 건수를 매번 세지 않음)
        List<Performance> found = performanceService.findPerformances(genre, region, keyword, status, after, pageSize + 1);
        boolean hasNext = found.size() > pageSize;
        List<Performance> content = hasNext ? found.subList(0, pageSize) : found;

        // 이 페이지 공연들의 회차 수를 한 번에 조회 (공연별 개별 조회 시 N+1)
        List<Long> performanceIds = content.stream()
                .map(Performance::getId)
                .collect(Collectors.toList());
        Map<Long, Long> scheduleCounts = performanceService.countUpcomingSchedules(performanceIds);

        // 엔티티 리스트를 안전하게 ListDto 리스트로 변환
        List<PerformanceListDto> collect = content.stream()
                .map(p -> new PerformanceListDto(p, scheduleCounts.getOrDefault(p.getId(), 0L)))
                .collect(Collectors.toList());

        // 다음 커서 = 이 페이지 마지막 공연의 (예매 상태, id). 예매 상태는 쿼리 정렬 기준과 같은 저장값(bookingGroup)
        PerformanceListDto last = collect.isEmpty() ? null : collect.get(collect.size() - 1);
        String nextCursor = hasNext ? PerformanceCursor.after(last.getBookingStatus(), last.getId()).encode() : null;
        // 전체 건수는 첫 페이지에서만 (이후 '더 보기'는 화면이 첫 응답의 값을 계속 씀)
        Long totalCount = after == null ? performanceService.countPerformances(genre, region, keyword, status) : null;

        return ListResult.withCursor(collect.size(), totalCount, nextCursor, collect);
    }

    // 회원용 API: 특정 공연 상세 조회
    @GetMapping("/api/v1/performances/{id}")
    public PerformanceDetailDto performanceDetailDto(@PathVariable("id") Long id) {
        Performance performance = performanceService.findOne(id);
        // 예매 상태 판정용 회차 수 (목록과 같은 조회 재사용)
        long upcomingScheduleCount = performanceService.countUpcomingSchedules(List.of(id)).getOrDefault(id, 0L);
        return new PerformanceDetailDto(performance, upcomingScheduleCount);
    }
}
