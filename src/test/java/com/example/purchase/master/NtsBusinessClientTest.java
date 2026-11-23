package com.example.purchase.master;

import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.nts.NtsApiException;
import com.example.purchase.master.nts.NtsBusinessClient;
import com.example.purchase.master.nts.NtsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** 국세청 API 클라이언트: 실제 국세청 대신 가짜 서버(MockRestServiceServer)로 응답을 흉내 낸다 */
class NtsBusinessClientTest {

    static final String BASE = "https://api.example.test/v1";
    static final String KEY_WITH_PLUS = "abc+def/ghi==";

    @Test
    @DisplayName("상태 코드 01/02/03/빈값 → 정상/휴업/폐업/확인 안 됨, 키의 + / = 는 %2B %2F %3D로 인코딩해서 보낸다")
    void mapStatuses() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE + "/status?serviceKey=abc%2Bdef%2Fghi%3D%3D"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.b_no[0]").value("1018100001"))
                .andRespond(withSuccess("""
                        {"status_code":"OK","match_cnt":3,"request_cnt":4,"data":[
                          {"b_no":"1018100001","b_stt":"계속사업자","b_stt_cd":"01","tax_type":"부가가치세 일반과세자"},
                          {"b_no":"1018100002","b_stt":"휴업자","b_stt_cd":"02","tax_type":"부가가치세 일반과세자"},
                          {"b_no":"1018100003","b_stt":"폐업자","b_stt_cd":"03","tax_type":"부가가치세 일반과세자","end_dt":"20250101"},
                          {"b_no":"1018100004","b_stt":"","b_stt_cd":"","tax_type":"국세청에 등록되지 않은 사업자등록번호입니다."}
                        ]}""", MediaType.APPLICATION_JSON));
        NtsBusinessClient client = new NtsBusinessClient(builder.build(), props(KEY_WITH_PLUS));

        Map<String, NtsBusinessClient.Result> r = client.fetchStatuses(
                List.of("1018100001", "1018100002", "1018100003", "1018100004"));

        assertThat(r.get("1018100001").status()).isEqualTo(VendorStatus.ACTIVE);
        assertThat(r.get("1018100002").status()).isEqualTo(VendorStatus.SUSPENDED);
        assertThat(r.get("1018100003").status()).isEqualTo(VendorStatus.CLOSED);
        assertThat(r.get("1018100004").status()).isEqualTo(VendorStatus.UNKNOWN);
        assertThat(r.get("1018100004").message()).contains("등록되지 않은");
        server.verify();
    }

    @Test
    @DisplayName("서버 오류(500)는 NtsApiException으로 바꿔 던진다 → 부른 쪽이 '확인 안 됨'으로 저장")
    void serverError() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/status"))).andRespond(withServerError());
        NtsBusinessClient client = new NtsBusinessClient(builder.build(), props("key"));

        assertThatThrownBy(() -> client.fetchStatuses(List.of("1018100001")))
                .isInstanceOf(NtsApiException.class);
    }

    @Test
    @DisplayName("키가 없으면 부르지 않는다")
    void noKey() {
        NtsBusinessClient client = new NtsBusinessClient(RestClient.create(), props(""));
        assertThat(client.isEnabled()).isFalse();
        assertThatThrownBy(() -> client.fetchStatuses(List.of("1018100001")))
                .isInstanceOf(NtsApiException.class).hasMessageContaining("NTS_SERVICE_KEY");
    }

    @Test
    @DisplayName("응답을 안 주는 서버: 읽기 타임아웃(0.3초)에 끊고 NtsApiException. 타임아웃이 없으면 영원히 기다린다")
    void readTimeout() throws Exception {
        try (ServerSocket silent = new ServerSocket(0)) {            // 연결은 받아 주지만 아무 응답도 안 하는 서버
            Thread acceptor = new Thread(() -> {
                try (Socket s = silent.accept()) {
                    Thread.sleep(5_000);
                } catch (Exception ignored) {
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(300));
            factory.setReadTimeout(Duration.ofMillis(300));
            RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + silent.getLocalPort())
                    .requestFactory(factory).build();
            NtsBusinessClient client = new NtsBusinessClient(restClient, props("key"));

            long start = System.nanoTime();
            assertThatThrownBy(() -> client.fetchStatuses(List.of("1018100001")))
                    .isInstanceOf(NtsApiException.class)
                    // ResourceAccessException일 줄 알았는데, 응답 본문을 읽다가 끊겨서 RestClientException으로 왔다.
                    // 그래서 클라이언트는 상위 타입 RestClientException을 잡는다. 근본 원인은 SocketTimeoutException
                    .hasRootCauseInstanceOf(java.net.SocketTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
        }
    }

    private static NtsProperties props(String key) {
        return new NtsProperties(BASE, key, null, null);
    }
}
