package com.example.purchase.config;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.MemberRepository;
import com.example.purchase.org.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 서버 첫 실행: 샘플 데이터 없이 뜨면 사용자가 0명이라 아무도 로그인할 수 없다.
 * 사용자가 한 명도 없고 ADMIN_INITIAL_PASSWORD가 있으면 관리자 계정(admin) 하나만 만든다.
 * 그 뒤로는 관리자 화면에서 부서·직급·사용자를 등록한다. 이미 사용자가 있으면 아무것도 안 한다.
 */
@Slf4j
@Component
@Order(4)   // 샘플 데이터(Order 2)가 먼저 돈다. 샘플이 있으면 여기서는 건너뛴다
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    static final String ADMIN_LOGIN_ID = "admin";

    private final MemberRepository memberRepository;
    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.initial-admin-password:}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (memberRepository.count() > 0) {
            return;
        }
        if (initialPassword == null || initialPassword.isBlank()) {
            log.warn("사용자가 한 명도 없습니다. ADMIN_INITIAL_PASSWORD를 정하고 다시 시작하면 관리자 계정(admin)을 만듭니다.");
            return;
        }
        if (initialPassword.length() < 8) {
            throw new IllegalStateException("ADMIN_INITIAL_PASSWORD는 8자 이상이어야 합니다.");
        }
        Department dept = departmentRepository.findByCode("D000")
                .orElseGet(() -> departmentRepository.save(Department.division("D000", "본사")));
        Position position = positionRepository.findAllByOrderByRankOrderAsc().stream().findFirst()
                .orElseGet(() -> positionRepository.save(new Position("관리자", 1)));
        memberRepository.save(new Member(ADMIN_LOGIN_ID, passwordEncoder.encode(initialPassword),
                "관리자", null, dept, position, Role.ADMIN));
        log.info("관리자 계정(admin)을 만들었습니다. 로그인한 뒤 [사용자 관리 → admin 수정]에서 비밀번호를 바꾸고 .env의 ADMIN_INITIAL_PASSWORD는 지워도 됩니다.");
    }
}
