package com.example.dongri.inmyticket.external;

import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceListResponse;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KopisService {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("http://www.kopis.or.kr/openApi/restful")
            .build();

    private final String apiKey = "test_api_key_1234";

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
                            .queryParam("rows", 10) // 뼈대 테스트용 10건만
                            .build())
                    .retrieve()
                    .bodyToMono(KopisPerformanceListResponse.class) // XML -> 자바 객체 자동 파싱
                    .block(); // 동기식 테스트를 위해 잠시 블로킹 처리

            if (response != null && response.getPerformances() != null) {
                log.info("KOPIS 외부 API 수신 성공! 대수: {}건", response.getPerformances().size());
                return response.getPerformances();
            }

        } catch (Exception e) {
            log.error("KOPIS API 연동 중 에러 발생: ", e);
        }

        return Collections.emptyList();
    }
    
}
