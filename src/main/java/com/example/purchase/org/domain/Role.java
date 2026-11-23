package com.example.purchase.org.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 사용자 역할. 한 사람은 역할 하나를 가진다 (요구사항 2장). */
@Getter
@RequiredArgsConstructor
public enum Role {
    EMPLOYEE("직원"),
    TEAM_LEADER("팀장"),
    DEPT_HEAD("부서장"),
    PURCHASING("구매팀"),
    ADMIN("관리자");

    private final String label;

    /** Spring Security 권한 이름. hasRole("ADMIN")은 내부적으로 "ROLE_ADMIN"을 찾는다 */
    public String authority() {
        return "ROLE_" + name();
    }
}
