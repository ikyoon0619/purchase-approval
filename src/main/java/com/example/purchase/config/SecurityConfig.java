package com.example.purchase.config;

import com.example.purchase.security.MenuAuthorizationManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 세션 기반 폼 로그인. 화면을 서버(Thymeleaf)에서 그리는 업무 시스템이라 JWT 대신 세션을 쓴다.
 * CSRF 보호는 켜 둔다 (Thymeleaf의 th:action 폼에 토큰이 자동으로 들어간다).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final MenuAuthorizationManager menuAuthorizationManager;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/favicon.ico", "/login", "/error").permitAll()
                        // 관리자 화면은 메뉴 권한과 상관없이 항상 ADMIN만 (권한 설정을 잘못해도 잠기지 않게)
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().access(menuAuthorizationManager))
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("loginId")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout"))
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
