package com.example.purchase.master.nts;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API는 반드시 타임아웃을 건다. 안 걸면 상대 서버가 응답을 안 줄 때 요청 스레드가 무한정 기다린다.
 * 연결 2초, 응답 3초 (application.yml app.nts.*)
 */
@Configuration
@EnableConfigurationProperties(NtsProperties.class)
public class NtsConfig {

    @Bean
    public NtsBusinessClient ntsBusinessClient(NtsProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
        return new NtsBusinessClient(restClient, properties);
    }
}
