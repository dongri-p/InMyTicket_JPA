package com.example.dongri.inmyticket.external;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceListResponse;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KopisService {

    private final WebClient webClient;
    private final String apiKey;

    public KopisService(
            WebClient.Builder webClientBuilder,
            @Value("${kopis.base-url}") String baseUrl,
            @Value("${kopis.api-key}") String apiKey) {
        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    // KOPIS 장르 코드(shcate): 연극, 뮤지컬, 서양음악(클래식), 한국음악(국악), 대중음악,
    // 무용(서양/한국무용), 대중무용, 서커스/마술, 복합
    private static final List<String> GENRE_CODES =
            List.of("AAAA", "GGGA", "CCCA", "CCCC", "CCCD", "BBBC", "BBBE", "EEEB", "EEEA");
    private static final int ROWS_PER_GENRE = 10;

    // 장르 구분 없이 최근 등록순으로만 가져오면 특정 장르(대중음악 등)에 몰려 장르 메뉴가 비므로,
    // 장르별로 최근 공연을 ROWS_PER_GENRE건씩 가져온다. 한 장르가 실패해도 나머지는 계속 진행
    public List<KopisPerformanceResponse> fetchRecentPerformances() {
        log.info("KOPIS 외부 API 호출 시작... (장르 {}개)", GENRE_CODES.size());

        List<KopisPerformanceResponse> result = new ArrayList<>();
        for (String genreCode : GENRE_CODES) {
            result.addAll(fetchPerformancesByGenre(genreCode));
        }

        log.info("KOPIS 외부 API 수신 완료. 총 {}건", result.size());
        return result;
    }

    private List<KopisPerformanceResponse> fetchPerformancesByGenre(String genreCode) {
        try {
            KopisPerformanceListResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/pblprfr") // 공연 목록 요청 엔드포인트
                            .queryParam("service", apiKey)
                            .queryParam("stdate", "20260101") // 2026년 이후 공연 조회
                            .queryParam("eddate", "20261231")
                            .queryParam("cpage", 1)
                            .queryParam("rows", ROWS_PER_GENRE)
                            .queryParam("shcate", genreCode)
                            .build())
                    .retrieve()
                    .bodyToMono(KopisPerformanceListResponse.class) // 자바 객체 자동 매핑
                    .block(); // 동기식 블로킹 처리

            if (response != null && response.getPerformances() != null) {
                return response.getPerformances();
            }

        } catch (Exception e) {
            log.warn("KOPIS 장르별 공연 목록 조회 실패. shcate={}, 원인={}", genreCode, e.getMessage());
        }

        return Collections.emptyList();
    }

    // 공연 상세(/pblprfr/{mt20id})에서 포스터 URL만 꺼냄. 목록 API는 최근 등록순이라
    // 예전에 저장된 공연은 다시 내려오지 않으므로, 포스터 backfill은 apiId로 상세를 직접 조회한다.
    // 실패하거나 데이터가 없으면(NODATA 등) null — 호출 측에서 건너뛰고 다음 기동 때 다시 시도됨
    public String fetchPosterUrl(String mt20id) {
        try {
            KopisPerformanceListResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/pblprfr/{mt20id}")
                            .queryParam("service", apiKey)
                            .build(mt20id))
                    .retrieve()
                    .bodyToMono(KopisPerformanceListResponse.class)
                    .block();

            if (response == null || response.getPerformances() == null || response.getPerformances().isEmpty()) {
                return null;
            }
            String poster = response.getPerformances().get(0).getPoster();
            return (poster == null || poster.isBlank()) ? null : poster;

        } catch (Exception e) {
            log.warn("KOPIS 공연 상세 조회 실패. mt20id={}, 원인={}", mt20id, e.getMessage());
            return null;
        }
    }
    
}
