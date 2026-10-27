package com.example.dongri.inmyticket.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 여러 apiId 중 이미 DB에 존재하는 것만 한 번에 조회 (N+1 방지)
    @Query("select p.apiId from Performance p where p.apiId in :apiIds")
    List<String> findApiIdsIn(@Param("apiIds") List<String> apiIds);

    // 포스터(V6)·공연 기간(V7)·지역(V8) 컬럼 추가 이전에 저장돼 값이 비어 있는 공연 (backfill 대상)
    List<Performance> findByPosterUrlIsNullOrStartDateIsNullOrAreaIsNull();

    // 공연 하나의 예매 상태 그룹: 0 예매중(시작 전 회차 있음) / 1 오픈예정(종료일이 오늘 이후거나 모름) / 2 공연종료
    // 목록 정렬과 상태 필터(BookingStatus.code)가 같은 식을 쓰도록 상수로 둠 (어노테이션 값도 상수끼리 이어 붙이는 건 가능)
    String BOOKING_GROUP = """
            case
              when exists (select 1 from Schedule s where s.performance = p and s.startTime > :now) then 0
              when p.endDate is null or p.endDate >= :today then 1
              else 2
            end""";

    // 장르·지역·검색어·예매 상태 필터 (모두 선택, status = -1이면 상태 무관)
    // keyword는 호출 측에서 %, _ 앞에 ! 를 붙여서 넘김 (Containing이 해 주던 이스케이프. 백슬래시는 DB마다 문자열 해석이 달라 ! 사용)
    String LIST_FILTER = """
            where (:allGenres = true or p.category in :categories)
              and (:allRegions = true or p.area in :areas)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')) escape '!')
            """
            // 텍스트 블록은 줄 끝 공백을 지우므로 BOOKING_GROUP과 이어 붙이는 부분은 일반 문자열로 씀 ("or case"가 "orcase"가 됨)
            + "  and (:status = -1 or " + BOOKING_GROUP + " = :status)";

    // 커서 이후만: (그룹, id)가 커서보다 뒤인 공연. cursorGroup = -1이면 첫 페이지
    String AFTER_CURSOR = " and (:cursorGroup = -1 or " + BOOKING_GROUP + " > :cursorGroup"
            + " or (" + BOOKING_GROUP + " = :cursorGroup and p.id > :cursorId))";

    // 공연 목록: 예매중 -> 오픈예정 -> 공연종료 순, 같은 그룹 안에서는 id 순.
    // OFFSET 대신 커서(직전 페이지 마지막 공연의 그룹·id) 이후를 읽어, 중간에 공연이 추가·삭제돼도 '더 보기'에 중복·누락이 없음
    // 정렬 기준이 회차 테이블에 있어 파생 쿼리 + Sort로는 표현이 안 되므로 JPQL로 직접 작성
    @Query("select p from Performance p " + LIST_FILTER + AFTER_CURSOR + " order by " + BOOKING_GROUP + ", p.id")
    List<Performance> findForListAfter(@Param("allGenres") boolean allGenres,
                                       @Param("categories") List<String> categories,
                                       @Param("allRegions") boolean allRegions,
                                       @Param("areas") List<String> areas,
                                       @Param("keyword") String keyword,
                                       @Param("status") int status,
                                       @Param("cursorGroup") int cursorGroup,
                                       @Param("cursorId") long cursorId,
                                       @Param("now") LocalDateTime now,
                                       @Param("today") LocalDate today,
                                       Pageable limit);

    @Query("select count(p) from Performance p " + LIST_FILTER)
    long countForList(@Param("allGenres") boolean allGenres,
                      @Param("categories") List<String> categories,
                      @Param("allRegions") boolean allRegions,
                      @Param("areas") List<String> areas,
                      @Param("keyword") String keyword,
                      @Param("status") int status,
                      @Param("now") LocalDateTime now,
                      @Param("today") LocalDate today);
}
