package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.external.TossPgClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

// 토스페이먼츠 API를 로컬 HTTP 서버로 흉내 내서, 요청 형식(경로·인증 헤더·본문)과 실패 응답 처리를 확인
public class TossPgClientTest {

    private static final String SECRET_KEY = "test_sk_dummy";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private final AtomicReference<String> requestAuthorization = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("승인 요청은 시크릿 키 Basic 인증으로 confirm API에 paymentKey·orderId·amount를 보낸다")
    void approve_sendsConfirmRequest() throws IOException {
        TossPgClient client = clientRespondingWith(200, "{\"status\":\"DONE\"}");

        client.approve("pay_key_1", "imt-7-abc123", 150000);

        Assertions.assertEquals("/v1/payments/confirm", requestPath.get());
        String expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString((SECRET_KEY + ":").getBytes(StandardCharsets.UTF_8));
        Assertions.assertEquals(expectedAuth, requestAuthorization.get());
        JsonNode body = objectMapper.readTree(requestBody.get());
        Assertions.assertEquals("pay_key_1", body.get("paymentKey").asText());
        Assertions.assertEquals("imt-7-abc123", body.get("orderId").asText());
        Assertions.assertEquals(150000, body.get("amount").asInt());
    }

    @Test
    @DisplayName("토스가 실패 응답을 주면 토스의 실패 문구를 담아 예외를 던진다")
    void approve_errorResponse_throwsWithTossMessage() throws IOException {
        TossPgClient client = clientRespondingWith(400,
                "{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"한도초과 혹은 잔액부족으로 결제에 실패했습니다.\"}");

        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> client.approve("pay_key_1", "imt-7-abc123", 150000));

        Assertions.assertTrue(thrown.getMessage().contains("한도초과 혹은 잔액부족"), thrown.getMessage());
    }

    @Test
    @DisplayName("취소 요청은 paymentKey 경로로 취소 사유와 함께 보낸다")
    void cancel_sendsCancelRequest() throws IOException {
        TossPgClient client = clientRespondingWith(200, "{\"status\":\"CANCELED\"}");

        client.cancel("pay_key_1", "고객 예매 취소");

        Assertions.assertEquals("/v1/payments/pay_key_1/cancel", requestPath.get());
        Assertions.assertEquals("고객 예매 취소",
                objectMapper.readTree(requestBody.get()).get("cancelReason").asText());
    }

    private TossPgClient clientRespondingWith(int status, String json) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new TossPgClient(WebClient.builder(), objectMapper, baseUrl, SECRET_KEY);
    }
}
