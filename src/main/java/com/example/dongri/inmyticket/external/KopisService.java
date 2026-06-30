package com.example.dongri.inmyticket.external;

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

    public List<KopisPerformanceResponse> fetchRecentPerformances() {
        try {
            log.info("KOPIS 외부 API 호출 시작...");

            KopisPerformanceListResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/pblprfr") // 공연 목록 요청 엔드포인트
                            .queryParam("service", apiKey)
                            .queryParam("stdate", "20260101") // 2026년 이후 공연 조회
                            .queryParam("eddate", "20261231")
                            .queryParam("cpage", 1)
                            .queryParam("rows", 10) // 테스트용 10건만
                            .build())
                    .retrieve()
                    .bodyToMono(KopisPerformanceListResponse.class) // 자바 객체 자동 매핑
                    .block(); // 동기식 블로킹 처리

            if (response != null && response.getPerformances() != null) {
                log.info("KOPIS 외부 API 수신 성공. 대수: {}건", response.getPerformances().size());
                return response.getPerformances();
            }

        } catch (Exception e) {
            log.error("KOPIS API 연동 중 에러 발생: ", e);
        }

        return Collections.emptyList();
    }
    
}
