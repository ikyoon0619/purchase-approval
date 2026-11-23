package com.example.purchase.master.nts;

import com.example.purchase.master.domain.VendorStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 국세청 사업자등록 상태조회 (POST /status, 한 번에 사업자번호 100개까지).
 * <pre>
 * 요청: {"b_no": ["1018100001", ...]}
 * 응답: {"status_code": "OK", "data": [{"b_no": "...", "b_stt_cd": "01", "b_stt": "계속사업자", "tax_type": "..."}]}
 * b_stt_cd: 01 계속사업자, 02 휴업자, 03 폐업자, "" 국세청에 등록되지 않은 번호
 * </pre>
 * 이 클래스는 HTTP만 다룬다. DB나 트랜잭션은 모른다 (트랜잭션 밖에서 부르도록: docs/troubleshooting/06).
 */
@Slf4j
public class NtsBusinessClient {

    public static final int MAX_PER_CALL = 100;

    private final RestClient restClient;
    private final NtsProperties properties;

    public NtsBusinessClient(RestClient restClient, NtsProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /** 키가 없으면 부르지 않는다 (설정 문제라 "API 실패"로 보지 않음) */
    public boolean isEnabled() {
        return properties.hasKey();
    }

    /** @return 사업자번호 → 결과. 요청한 번호가 응답에 없으면 그 번호는 빠진다 */
    public Map<String, Result> fetchStatuses(List<String> bizNos) {
        if (!properties.hasKey()) {
            throw new NtsApiException("국세청 API 키(NTS_SERVICE_KEY)가 설정되지 않았습니다.");
        }
        if (bizNos.isEmpty() || bizNos.size() > MAX_PER_CALL) {
            throw new IllegalArgumentException("사업자번호는 1~" + MAX_PER_CALL + "개");
        }
        StatusResponse body;
        try {
            body = restClient.post()
                    // 키를 "{serviceKey}" 변수로 넘겨야 +, /, = 까지 전부 인코딩된다 (%2B, %2F, %3D).
                    // queryParam("serviceKey", 키)처럼 값을 바로 넣으면 +가 그대로 나가서 서버가 공백으로 읽는다 → 키 불일치.
                    // 포털의 Encoding 키(이미 %2B로 바뀐 것)를 넣으면 두 번 인코딩된다 → 반드시 Decoding 키를 쓴다.
                    .uri(b -> b.path("/status").queryParam("serviceKey", "{serviceKey}").build(properties.serviceKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("b_no", bizNos))
                    .retrieve()
                    .body(StatusResponse.class);
        } catch (RestClientException e) {
            // 연결 실패·타임아웃(ResourceAccessException, 또는 응답을 읽다 끊기면 RestClientException), 4xx/5xx 모두 여기로
            log.warn("국세청 API 호출 실패: {}", e.getMessage());
            throw new NtsApiException("국세청 API를 부르지 못했습니다: " + e.getClass().getSimpleName(), e);
        }
        if (body == null || !"OK".equals(body.statusCode()) || body.data() == null) {
            throw new NtsApiException("국세청 API 응답이 올바르지 않습니다: " + (body == null ? "빈 응답" : body.statusCode()));
        }
        Map<String, Result> result = new LinkedHashMap<>();
        for (StatusItem item : body.data()) {
            result.put(item.bNo(), new Result(item.bNo(), toStatus(item.bSttCd()), describe(item)));
        }
        return result;
    }

    static VendorStatus toStatus(String code) {
        if (code == null) {
            return VendorStatus.UNKNOWN;
        }
        return switch (code) {
            case "01" -> VendorStatus.ACTIVE;
            case "02" -> VendorStatus.SUSPENDED;
            case "03" -> VendorStatus.CLOSED;
            default -> VendorStatus.UNKNOWN; // 등록되지 않은 번호 등
        };
    }

    private static String describe(StatusItem item) {
        return item.bStt() != null && !item.bStt().isBlank() ? item.bStt() : item.taxType();
    }

    public record Result(String bizNo, VendorStatus status, String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusResponse(@JsonProperty("status_code") String statusCode, List<StatusItem> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatusItem(@JsonProperty("b_no") String bNo, @JsonProperty("b_stt") String bStt,
                      @JsonProperty("b_stt_cd") String bSttCd, @JsonProperty("tax_type") String taxType) {
    }
}
