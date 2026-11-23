package com.example.purchase.approval;

import com.example.purchase.security.LoginUser;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 화면 흐름: 상세에서 상신 → 결재함에 보임 → 승인. 실패는 오류 메시지를 들고 상세로 돌아간다 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApprovalControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    RequestFixture f;
    Long id;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        id = f.draft(f.dev, "노트북 구매").getId();
        em.flush();
    }

    @Test
    @DisplayName("임시저장 상세에는 결재선 미리보기와 상신 버튼, 상신 후에는 결재함과 승인 버튼")
    void submitThenInbox() throws Exception {
        mockMvc.perform(get("/requests/" + id).with(user(new LoginUser(f.dev))))
                .andExpect(content().string(containsString("상신하면 아래 순서로 결재가 올라갑니다.")))
                .andExpect(content().string(containsString("이팀장 과장")));

        mockMvc.perform(post("/requests/" + id + "/submit").with(user(new LoginUser(f.dev))).with(csrf()))
                .andExpect(redirectedUrl("/requests/" + id))
                .andExpect(flash().attribute("toast", containsString("상신했습니다")));
        em.flush();
        em.clear();

        mockMvc.perform(get("/approvals").with(user(new LoginUser(f.devLead))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("노트북 구매")));
        mockMvc.perform(get("/requests/" + id).with(user(new LoginUser(f.devLead))))
                .andExpect(content().string(containsString("결재 의견")));
        mockMvc.perform(get("/requests/" + id).with(user(new LoginUser(f.bizHead))))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("결재 의견")))); // 아직 본부장 차례가 아님
    }

    @Test
    @DisplayName("반려 의견을 비우면 상세로 돌아가 오류 메시지를 보여 준다")
    void rejectWithoutComment() throws Exception {
        mockMvc.perform(post("/requests/" + id + "/submit").with(user(new LoginUser(f.dev))).with(csrf()));
        em.flush();
        em.clear();

        mockMvc.perform(post("/requests/" + id + "/reject").param("comment", "")
                        .with(user(new LoginUser(f.devLead))).with(csrf()))
                .andExpect(redirectedUrl("/requests/" + id))
                .andExpect(flash().attribute("error", "반려할 때는 의견을 꼭 적어 주세요."));
    }

    @Test
    @DisplayName("결재함 메뉴는 직원에게 403 (메뉴 권한)")
    void inboxForbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/approvals").with(user(new LoginUser(f.dev))))
                .andExpect(status().isForbidden());
    }
}
