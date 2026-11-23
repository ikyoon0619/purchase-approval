package com.example.purchase.menu;

import com.example.purchase.menu.domain.Menu;
import com.example.purchase.org.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 엔티티 단위 테스트: 스프링 없이 자바 객체만으로 규칙을 확인한다 (가장 빠름) */
class MenuTest {

    @Test
    @DisplayName("관리자 메뉴는 권한을 모두 빼도 관리자 권한이 남는다")
    void adminMenuKeepsAdmin() {
        Menu menu = new Menu("사용자 관리", "/admin/members", 900, EnumSet.of(Role.ADMIN));

        menu.changeRoles(Set.of());

        assertThat(menu.isAllowed(Role.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("일반 메뉴는 체크한 역할로 바뀐다")
    void changeRoles() {
        Menu menu = new Menu("결재함", "/approvals", 30, EnumSet.of(Role.TEAM_LEADER));

        menu.changeRoles(Set.of(Role.DEPT_HEAD, Role.PURCHASING));

        assertThat(menu.getRoles()).containsExactlyInAnyOrder(Role.DEPT_HEAD, Role.PURCHASING);
    }
}
