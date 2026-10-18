package com.example.dongri.inmyticket.external;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.example.dongri.inmyticket.domain.PgProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

// 토스페이먼츠 결제 승인/취소 API 연동 (테스트 키를 쓰면 결제창·승인 과정은 실제와 같고 청구만 되지 않음).
// 결제창은 프론트에서 띄우고, 서버는 결제창이 돌려준 paymentKey·orderId·amount로 최종 승인(confirm)만 함.
// 승인 요청을 서버에서 해야 금액을 프론트가 조작해도 예약 금액과 비교해 막을 수 있음
@Slf4j
@Component
public class TossPgClient implements PgClient {

    // 토스 권장 승인 응답 대기 시간은 길게 잡혀 있어서 넉넉히 둠. 넘기면 실패로 보고 예약을 PENDING으로 되돌림
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public TossPgClient(
            WebClient.Builder webClientBuilder,
            ObjectMapper objectMapper,
            @Value("${toss.base-url}") String baseUrl,
            @Value("${toss.secret-key}") String secretKey) {
        // 시크릿 키 뒤에 ':'을 붙여 base64로 인코딩한 Basic 인증 (토스 API 인증 방식)
        String encoded = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public PgProvider provider() {
        return PgProvider.TOSS;
    }

    @Override
    public void approve(String paymentKey, String orderId, int amount) {
        post("/v1/payments/confirm",
                Map.of("paymentKey", paymentKey, "orderId", orderId, "amount", amount),
                "결제 승인");
        log.info("토스페이먼츠 결제 승인 완료. orderId={}", orderId);
    }

    @Override
    public void cancel(String paymentKey, String reason) {
        post("/v1/payments/{paymentKey}/cancel", Map.of("cancelReason", reason), "결제 취소", paymentKey);
        log.info("토스페이먼츠 결제 취소(환불) 완료. reason={}", reason);
    }

    private void post(String uri, Map<String, Object> body, String action, Object... uriVariables) {
        try {
            webClient.post()
                    .uri(uri, uriVariables)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .toBodilessEntity()
                    .block(TIMEOUT);
        } catch (WebClientResponseException e) {
            // 토스는 실패 시 {"code": "...", "message": "..."}로 이유를 내려줌 -> 사용자에게 그 문구를 보여줌
            String message = readTossMessage(e.getResponseBodyAsString());
            log.warn("토스페이먼츠 {} 실패. status={}, body={}", action, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException(action + "에 실패했습니다. " + message, e);
        } catch (RuntimeException e) {
            // 타임아웃·연결 실패: 승인 여부를 알 수 없지만 결제 기록이 없으므로 미승인으로 봄 (README 한계에 적어 둠)
            log.warn("토스페이먼츠 {} 통신 오류. 원인={}", action, e.getMessage());
            throw new IllegalStateException(action + " 통신 중 오류가 발생했습니다.", e);
        }
    }

    private String readTossMessage(String responseBody) {
        try {
            JsonNode message = objectMapper.readTree(responseBody).get("message");
            return message != null ? message.asText() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
