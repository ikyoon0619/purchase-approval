package com.example.purchase.security;

import com.example.purchase.menu.domain.Menu;
import com.example.purchase.menu.repository.MenuRepository;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.support.OrgFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 로그인, 역할별 접근, 메뉴 권한을 실제 필터 체인으로 확인한다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired MenuRepository menuRepository;

    Member admin;
    Member employee;
    Department team;
    Position position;

    @BeforeEach
    void setUp() {
        OrgFixture f = new OrgFixture(em);
        team = f.team("D210", "개발팀", f.division("D200", "사업본부"));
        position = f.position("사원", 1);
        admin = f.member("admin", "관리자", team, position, Role.ADMIN);
        employee = f.member("dev1", "김개발", team, position, Role.EMPLOYEE);
        em.flush();
    }

    @Test
    @DisplayName("로그인하지 않으면 로그인 화면으로 보낸다")
    void anonymous_redirectToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("아이디·비밀번호가 맞으면 로그인되고 첫 화면으로 간다")
    void login_success() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("loginId").user("dev1").password(OrgFixture.PASSWORD))
                .andExpect(authenticated().withUsername("dev1"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 로그인 화면에 오류를 보여 준다")
    void login_wrongPassword() throws Exception {
        mockMvc.perform(formLogin("/login").userParameter("loginId").user("dev1").password("wrong"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("사용 중지된 계정은 비밀번호가 맞아도 로그인할 수 없다")
    void login_disabled() throws Exception {
        employee.update(employee.getName(), null, team, position, Role.EMPLOYEE, false);
        em.flush();

        mockMvc.perform(formLogin("/login").userParameter("loginId").user("dev1").password(OrgFixture.PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    @DisplayName("직원은 관리자 화면에 들어갈 수 없다 (403)")
    void employee_cannotOpenAdmin() throws Exception {
        mockMvc.perform(get("/admin/members").with(user(new LoginUser(employee))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자는 사용자 목록을 보고, 왼쪽 메뉴에 관리자 메뉴가 보인다")
    void admin_canOpenAdmin() throws Exception {
        mockMvc.perform(get("/admin/members").with(user(new LoginUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("김개발")))
                .andExpect(content().string(containsString("메뉴 권한")));
    }

    @Test
    @DisplayName("직원의 왼쪽 메뉴에는 관리자 메뉴가 없다")
    void employee_sidebarHasNoAdminMenu() throws Exception {
        mockMvc.perform(get("/").with(user(new LoginUser(employee))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("대시보드")))
                .andExpect(content().string(not(containsString("사용자 관리"))));
    }

    @Test
    @DisplayName("메뉴 권한에 없는 역할은 주소를 직접 입력해도 막힌다")
    void menuPermission_blocksUrl() throws Exception {
        menuRepository.save(new Menu("통계", "/stats", 50, EnumSet.of(Role.ADMIN)));

        mockMvc.perform(get("/stats/monthly").with(user(new LoginUser(employee))))
                .andExpect(status().isForbidden());
        // 관리자는 권한 검사를 통과한다 (9단계에서 통계 화면이 생겼다)
        mockMvc.perform(get("/stats/monthly").with(user(new LoginUser(admin))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("관리자가 사용자를 등록하면 목록으로 돌아간다. CSRF 토큰이 없으면 거절된다")
    void admin_createMember() throws Exception {
        mockMvc.perform(post("/admin/members").with(user(new LoginUser(admin))).with(csrf())
                        .param("loginId", "newbie")
                        .param("password", "abcd1234")
                        .param("name", "신입")
                        .param("departmentId", team.getId().toString())
                        .param("positionId", position.getId().toString())
                        .param("role", "EMPLOYEE")
                        .param("enabled", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/members"));

        mockMvc.perform(post("/admin/members").with(user(new LoginUser(admin)))
                        .param("loginId", "hacker"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이미 있는 아이디로 등록하면 폼에 오류 메시지를 보여 준다")
    void admin_createMember_duplicate() throws Exception {
        mockMvc.perform(post("/admin/members").with(user(new LoginUser(admin))).with(csrf())
                        .param("loginId", "dev1")
                        .param("password", "abcd1234")
                        .param("name", "중복")
                        .param("departmentId", team.getId().toString())
                        .param("positionId", position.getId().toString())
                        .param("role", "EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("이미 쓰고 있는 아이디입니다.")));
    }
}
