package com.example.dongri.inmyticket.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 여러 apiId 중 이미 DB에 존재하는 것만 한 번에 조회 (N+1 방지)
    @Query("select p.apiId from Performance p where p.apiId in :apiIds")
    List<String> findApiIdsIn(@Param("apiIds") List<String> apiIds);

    // 포스터(V6)·공연 기간(V7)·지역(V8) 컬럼 추가 이전에 저장돼 값이 비어 있는 공연 (backfill 대상)
    List<Performance> findByPosterUrlIsNullOrStartDateIsNullOrAreaIsNull();

    // 장르·지역·검색어·예매 상태 필터 (모두 선택, status = -1이면 상태 무관)
    // keyword는 호출 측에서 %, _ 앞에 ! 를 붙여서 넘김 (Containing이 해 주던 이스케이프. 백슬래시는 DB마다 문자열 해석이 달라 ! 사용)
    // 예매 상태는 저장된 그룹(p.bookingGroup) 기준. 요청마다 회차를 뒤져 계산하던 방식은 인덱스를 못 타서 바꿈 (V10 참고)
    String LIST_FILTER = """
            where (:allGenres = true or p.category in :categories)
              and (:allRegions = true or p.area in :areas)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')) escape '!')
              and (:status = -1 or p.bookingGroup = :status)
            """;

    // 커서 이후만: (그룹, id)가 커서보다 뒤인 공연. cursorGroup = -1이면 첫 페이지
    String AFTER_CURSOR = """
              and (:cursorGroup = -1 or p.bookingGroup > :cursorGroup
                   or (p.bookingGroup = :cursorGroup and p.id > :cursorId))
            """;

    // 공연 목록: 예매중 -> 오픈예정 -> 공연종료 순, 같은 그룹 안에서는 id 순.
    // OFFSET 대신 커서(직전 페이지 마지막 공연의 그룹·id) 이후를 읽어, 중간에 공연이 추가·삭제돼도 '더 보기'에 중복·누락이 없음
    // (booking_group, performance_id) 인덱스 순서 그대로 읽으므로 정렬 없이 limit 건만 읽고 끝남
    @Query("select p from Performance p " + LIST_FILTER + AFTER_CURSOR + " order by p.bookingGroup, p.id")
    List<Performance> findForListAfter(@Param("allGenres") boolean allGenres,
                                       @Param("categories") List<String> categories,
                                       @Param("allRegions") boolean allRegions,
                                       @Param("areas") List<String> areas,
                                       @Param("keyword") String keyword,
                                       @Param("status") int status,
                                       @Param("cursorGroup") int cursorGroup,
                                       @Param("cursorId") long cursorId,
                                       Pageable limit);

    @Query("select count(p) from Performance p " + LIST_FILTER)
    long countForList(@Param("allGenres") boolean allGenres,
                      @Param("categories") List<String> categories,
                      @Param("allRegions") boolean allRegions,
                      @Param("areas") List<String> areas,
                      @Param("keyword") String keyword,
                      @Param("status") int status);

    // 마지막 회차가 시작돼 더는 예매중이 아닌 공연을 오픈예정/공연종료로 내림 (BookingGroupScheduler)
    // 예매중 그룹만 인덱스로 좁혀서 보므로 공연이 많아도 가벼움. 규칙은 BookingStatus.of와 같음
    @Modifying
    @Query("""
            update Performance p
            set p.bookingGroup = case when p.endDate is null or p.endDate >= :today then 1 else 2 end
            where p.bookingGroup = 0 and (p.lastScheduleAt is null or p.lastScheduleAt <= :now)
            """)
    int demoteFinishedOnSale(@Param("now") LocalDateTime now, @Param("today") LocalDate today);

    // 종료일이 지난 오픈예정 공연을 공연종료로 내림 (BookingGroupScheduler)
    @Modifying
    @Query("update Performance p set p.bookingGroup = 2 where p.bookingGroup = 1 and p.endDate < :today")
    int demoteEndedUpcoming(@Param("today") LocalDate today);
}
