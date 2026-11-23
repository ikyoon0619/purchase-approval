package com.example.purchase.audit;

import com.example.purchase.security.LoginUser;
import com.example.purchase.support.DbCleaner;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 처리 못 한 예외는 error_log에 저장되고, 사용자는 오류 번호를, 관리자는 스택을 본다 (OPS-02) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ErrorLogTest.BoomController.class)
class ErrorLogTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    RequestFixture f;
    LoginUser admin;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> {
            f = new RequestFixture(em);
            var a = new com.example.purchase.org.domain.Member("admin", "pw", "관리자", null, f.dev.getDepartment(),
                    f.dev.getPosition(), com.example.purchase.org.domain.Role.ADMIN);
            em.persist(a);
            em.flush();
            admin = new LoginUser(em.createQuery("select m from Member m join fetch m.department where m.loginId = 'admin'",
                    com.example.purchase.org.domain.Member.class).getSingleResult());
        });
    }

    @AfterEach
    void cleanUp() {
        DbCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("예상 못 한 예외: 사용자에게 오류 번호, error_log에 어디서·누가·스택, 관리자 화면에서 조회")
    void unexpectedErrorIsStored() throws Exception {
        mockMvc.perform(get("/test/boom").with(user(new LoginUser(f.dev))))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("오류 번호")));

        Map<String, Object> row = jdbc.queryForMap("select id, source, login_id, exception_class, stack_trace from error_log");
        assertThat(row.get("source")).isEqualTo("GET /test/boom");
        assertThat(row.get("login_id")).isEqualTo("dev1");
        assertThat(row.get("exception_class")).isEqualTo("java.lang.IllegalStateException");
        assertThat((String) row.get("stack_trace")).contains("BoomController.boom");

        mockMvc.perform(get("/admin/error-logs").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("IllegalStateException")));
        mockMvc.perform(get("/admin/error-logs/" + row.get("id")).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("BoomController.boom")));
        mockMvc.perform(get("/admin/error-logs").with(user(new LoginUser(f.dev))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("업무 규칙 위반(BusinessException)은 에러 로그에 남기지 않는다. 감사 로그 화면은 실패 시도를 보여 준다")
    void businessErrorIsNotErrorLog() throws Exception {
        Long id = tx.execute(s -> f.draft(f.dev, "노트북").getId());
        mockMvc.perform(get("/requests/" + id).with(user(new LoginUser(f.sales))))
                .andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select count(*) from error_log", Long.class)).isZero();
        mockMvc.perform(get("/admin/audit-logs").param("success", "false").with(user(admin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("[막힌 것] /requests/abc 같은 잘못된 주소는 500·에러 로그가 아니라 400")
    void badPathIsNot500() throws Exception {
        mockMvc.perform(get("/requests/abc").with(user(new LoginUser(f.dev))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("주소나 입력값이 올바르지 않습니다.")));
        assertThat(jdbc.queryForObject("select count(*) from error_log", Long.class)).isZero();
    }

    @TestConfiguration
    @RestController
    static class BoomController {
        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("일부러 낸 오류");
        }
    }
}
