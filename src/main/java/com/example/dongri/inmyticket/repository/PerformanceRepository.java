package com.example.dongri.inmyticket.repository;

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

    // 포스터 컬럼(V6) 추가 이전에 저장돼 posterUrl이 비어 있는 공연 (backfill 대상)
    List<Performance> findByPosterUrlIsNull();

    // 장르 메뉴 필터: 장르 묶음(GenreGroup)에 속한 KOPIS 장르명 중 하나인 공연만 페이지 조회
    Page<Performance> findByCategoryIn(List<String> categories, Pageable pageable);

    // 헤더 검색창: 제목에 검색어가 포함된 공연 (Containing은 검색어의 %, _를 이스케이프해 줌)
    Page<Performance> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);

    // 장르 메뉴 + 검색어를 같이 쓴 경우
    Page<Performance> findByCategoryInAndTitleContainingIgnoreCase(List<String> categories, String keyword, Pageable pageable);
}
