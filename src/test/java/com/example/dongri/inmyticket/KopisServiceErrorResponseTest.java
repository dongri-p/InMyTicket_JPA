package com.example.dongri.inmyticket;

import com.example.dongri.inmyticket.external.KopisService;
import com.example.dongri.inmyticket.external.dto.KopisPerformanceResponse;
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
import java.util.List;

// KOPIS는 키 오류여도 HTTP 200 + 정상 응답과 같은 <dbs><db> 구조로 내려줌 -> 공연으로 파싱되면 안 됨
// 로컬 HTTP 서버로 KOPIS 응답을 흉내 냄 (응답 XML은 실제 KOPIS에 등록 안 된 키로 호출해 받은 그대로)
public class KopisServiceErrorResponseTest {

    private static final String ERROR_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><dbs><db><returncode>02</returncode>"
                    + "<errmsg>SERVICE KEY IS NOT REGISTERED ERROR</errmsg><responsetime>2026-10-15 13:51:01</responsetime></db></dbs>";

    private static final String PERFORMANCE_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><dbs><db><mt20id>PF303377</mt20id>"
                    + "<prfnm>꽃의 비밀 [이천]</prfnm><prfpdfrom>2026.12.11</prfpdfrom><prfpdto>2026.12.12</prfpdto>"
                    + "<fcltynm>이천아트홀</fcltynm><genrenm>연극</genrenm><prfstate>공연예정</prfstate></db></dbs>";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("키 오류 응답이면 공연 목록을 빈 리스트로 돌려준다")
    void fetchRecentPerformances_errorResponse_returnsEmpty() throws IOException {
        KopisService kopisService = kopisServiceRespondingWith(ERROR_XML);

        List<KopisPerformanceResponse> result = kopisService.fetchRecentPerformances();

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("키 오류 응답이면 공연 상세는 null")
    void fetchDetail_errorResponse_returnsNull() throws IOException {
        KopisService kopisService = kopisServiceRespondingWith(ERROR_XML);

        Assertions.assertNull(kopisService.fetchDetail("PF303377"));
    }

    @Test
    @DisplayName("정상 응답은 그대로 공연으로 파싱된다")
    void fetchDetail_normalResponse_parsed() throws IOException {
        KopisService kopisService = kopisServiceRespondingWith(PERFORMANCE_XML);

        KopisPerformanceResponse detail = kopisService.fetchDetail("PF303377");

        Assertions.assertNotNull(detail);
        Assertions.assertFalse(detail.isError());
        Assertions.assertEquals("PF303377", detail.getMt20id());
        Assertions.assertEquals("꽃의 비밀 [이천]", detail.getPrfnm());
    }

    private KopisService kopisServiceRespondingWith(String xml) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = xml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/xml;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        return new KopisService(WebClient.builder(), baseUrl, "test-key");
    }
}
