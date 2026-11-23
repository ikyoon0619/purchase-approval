package com.example.purchase.stats;

import com.example.purchase.notification.domain.Notification;
import com.example.purchase.security.LoginUser;
import com.example.purchase.stats.domain.MonthlyPurchaseStat;
import com.example.purchase.support.RequestFixture;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 대시보드·통계·알림 화면. 통계 표는 배치 대신 테스트가 직접 넣는다 (배치 자체는 NightlyBatchTest) */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    RequestFixture f;
    Notification notice;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        String month = YearMonth.now().toString();
        em.persist(new MonthlyPurchaseStat(month, f.dev.getDepartment(), 2, new BigDecimal("5617000"), LocalDateTime.now()));
        em.persist(new MonthlyPurchaseStat(month, f.sales.getDepartment(), 1, new BigDecimal("3967000"), LocalDateTime.now()));
        notice = new Notification(f.dev, "[PR-TEST] 노트북: 팀장 결재를 4일째 기다리고 있습니다.", "/requests/1", "TEST:1");
        em.persist(notice);
        em.flush();
    }

    @Test
    @DisplayName("대시보드: 우리 부서 이번 달 금액, 부서별 막대, 안 읽은 알림 수와 최근 알림")
    void dashboard() throws Exception {
        mockMvc.perform(get("/").with(user(new LoginUser(f.dev))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5,617,000원")))
                .andExpect(content().string(containsString("영업팀")))
                .andExpect(content().string(containsString("4일째 기다리고")))
                .andExpect(content().string(containsString("badge count")));
    }

    @Test
    @DisplayName("통계 화면: 직원 403, 부서장은 보기만, 구매팀은 [다시 집계] 버튼까지")
    void statsAccess() throws Exception {
        mockMvc.perform(get("/stats/monthly").with(user(new LoginUser(f.dev))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/stats/monthly").with(user(new LoginUser(f.bizHead))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("5,617,000")))
                .andExpect(content().string(not(containsString("다시 집계</button>"))));
        mockMvc.perform(get("/stats/monthly").with(user(new LoginUser(f.buyer))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("다시 집계</button>")));
        mockMvc.perform(post("/stats/monthly/rerun").param("month", YearMonth.now().toString())
                        .with(user(new LoginUser(f.bizHead))).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("알림을 누르면 읽음이 되고 관련 화면으로 간다. 남의 알림은 못 연다")
    void openNotification() throws Exception {
        mockMvc.perform(post("/notifications/" + notice.getId() + "/open").with(user(new LoginUser(f.sales))).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/notifications/" + notice.getId() + "/open").with(user(new LoginUser(f.dev))).with(csrf()))
                .andExpect(redirectedUrl("/requests/1"));

        em.flush();
        em.clear();
        assertThat(em.find(Notification.class, notice.getId()).isRead()).isTrue();
    }
}
