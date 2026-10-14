package com.example.dongri.inmyticket.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dongri.inmyticket.domain.Performance;

public interface PerformanceRepository extends JpaRepository<Performance, Long> {

    // 여러 apiId 중 이미 DB에 존재하는 것만 한 번에 조회 (N+1 방지)
    @Query("select p.apiId from Performance p where p.apiId in :apiIds")
    List<String> findApiIdsIn(@Param("apiIds") List<String> apiIds);

    // 포스터(V6)·공연 기간(V7) 컬럼 추가 이전에 저장돼 값이 비어 있는 공연 (backfill 대상)
    List<Performance> findByPosterUrlIsNullOrStartDateIsNull();

    // 공연 목록: 장르·검색어 필터(둘 다 선택) + "예매중 -> 공연 예정/진행 중 -> 공연 종료" 순 정렬
    // - 예매중 = 아직 시작 전인 회차가 있음 (목록 배지와 같은 기준). 그 다음 종료일이 오늘 이후거나 모르는 공연, 끝난 공연은 맨 뒤
    // - 같은 그룹 안에서는 id 순으로 고정해 '더 보기' 페이지 사이에 중복·누락이 없게 함
    // - 정렬 기준이 회차 테이블에 있어 파생 쿼리 + Sort로는 표현이 안 되므로 JPQL로 직접 작성
    // - keyword는 호출 측에서 %, _ 앞에 ! 를 붙여서 넘김 (Containing이 해 주던 이스케이프. 백슬래시는 DB마다 문자열 해석이 달라 ! 사용)
    @Query(value = """
            select p from Performance p
            where (:allGenres = true or p.category in :categories)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')) escape '!')
            order by
              case
                when exists (select 1 from Schedule s where s.performance = p and s.startTime > :now) then 0
                when p.endDate is null or p.endDate >= :today then 1
                else 2
              end,
              p.id
            """,
            countQuery = """
            select count(p) from Performance p
            where (:allGenres = true or p.category in :categories)
              and (:keyword is null or lower(p.title) like lower(concat('%', :keyword, '%')) escape '!')
            """)
    Page<Performance> findForList(@Param("allGenres") boolean allGenres,
                                  @Param("categories") List<String> categories,
                                  @Param("keyword") String keyword,
                                  @Param("now") LocalDateTime now,
                                  @Param("today") LocalDate today,
                                  Pageable pageable);
}
