package com.example.purchase.order;

import com.example.purchase.approval.service.ApprovalLineResolver;
import com.example.purchase.inventory.service.InventoryService;
import com.example.purchase.request.domain.PurchaseRequest;
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

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 화면 흐름: 발주 대기 → 발주 → 입고 → 재고 현황. 직원은 메뉴 권한으로 403 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;
    @Autowired ApprovalLineResolver resolver;
    @Autowired InventoryService inventoryService;

    RequestFixture f;
    PurchaseRequest approved;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        inventoryService.fillMissing();
        approved = f.draft(f.dev, "노트북 구매");
        approved.submit(resolver.resolve(approved), LocalDateTime.now());
        approved.approve(f.devLead.getId(), null, LocalDateTime.now());
        approved.approve(f.bizHead.getId(), null, LocalDateTime.now());
        em.flush();
    }

    @Test
    @DisplayName("구매팀: 발주 대기 목록에서 발주 → 발주서에서 입고 → 재고 현황에 반영")
    void orderAndReceive() throws Exception {
        LoginUser buyer = new LoginUser(f.buyer);
        mockMvc.perform(get("/orders").with(user(buyer)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("노트북 구매")));

        String location = mockMvc.perform(post("/orders").param("requestId", approved.getId().toString())
                        .with(user(buyer)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();
        em.flush();
        em.clear();

        mockMvc.perform(post(location + "/receive").with(user(buyer)).with(csrf()))   // 수량 안 적으면 발주 수량 그대로
                .andExpect(redirectedUrl(location))
                .andExpect(flash().attribute("toast", containsString("입고 처리했습니다")));
        em.flush();
        em.clear();

        mockMvc.perform(get("/inventory").param("onlyInStock", "true").with(user(buyer)))
                .andExpect(content().string(containsString("노트북")))
                .andExpect(content().string(containsString("모니터")));
    }

    @Test
    @DisplayName("직원은 발주 관리·재고 현황에 들어갈 수 없다")
    void forbiddenForEmployee() throws Exception {
        mockMvc.perform(get("/orders").with(user(new LoginUser(f.dev)))).andExpect(status().isForbidden());
        mockMvc.perform(get("/inventory").with(user(new LoginUser(f.dev)))).andExpect(status().isForbidden());
    }
}
