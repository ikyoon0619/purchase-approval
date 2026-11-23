package com.example.purchase.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 작성자 값은 {@link com.example.purchase.security.SecurityAuditorAware}가 넣어 준다.
 * (2단계에서는 여기서 "system"만 돌려줬다)
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaConfig {
}
