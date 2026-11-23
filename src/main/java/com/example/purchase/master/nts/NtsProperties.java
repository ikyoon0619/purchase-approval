package com.example.purchase.master.nts;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 국세청 사업자등록 상태조회 API 설정 (공공데이터포털).
 * serviceKey는 포털에서 받은 "Decoding" 키를 .env의 NTS_SERVICE_KEY에 넣는다. 코드와 git에는 넣지 않는다.
 */
@ConfigurationProperties(prefix = "app.nts")
public record NtsProperties(String baseUrl, String serviceKey, Duration connectTimeout, Duration readTimeout) {

    public NtsProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.odcloud.kr/api/nts-businessman/v1";
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }

    public boolean hasKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }
}
