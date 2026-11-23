package com.example.purchase.request;

import com.example.purchase.security.LoginUser;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 화면 흐름: 폼 전송(multipart) → 상세, 입력 오류, 권한 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RequestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    RequestFixture f;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
    }

    @Test
    @DisplayName("작성 폼을 보내면 상세 화면으로 가고, 화면이 보낸 합계는 무시된다")
    void createFlow() throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "견적서.pdf", "application/pdf", new byte[]{1});
        String location = mockMvc.perform(multipart("/requests").file(file)
                        .param("title", "모니터 구매")
                        .param("purposeCode", "WORK")
                        .param("vendorId", f.activeVendor.getId().toString())
                        .param("items[0].itemId", f.monitor.getId().toString())
                        .param("items[0].quantity", "3")
                        .param("items[0].unitPrice", "300000")
                        .param("totalAmount", "1")                    // 조작된 값: 폼에 없는 필드라 무시
                        .with(user(new LoginUser(f.dev))).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn().getResponse().getRedirectedUrl();

        mockMvc.perform(get(location).with(user(new LoginUser(f.dev))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("900,000원")))
                .andExpect(content().string(containsString("견적서.pdf")));
    }

    @Test
    @DisplayName("수량 0이면 그 줄에 오류 메시지를 붙여 다시 보여 준다")
    void validationError() throws Exception {
        mockMvc.perform(multipart("/requests")
                        .param("title", "모니터 구매")
                        .param("purposeCode", "WORK")
                        .param("vendorId", f.activeVendor.getId().toString())
                        .param("items[0].itemId", f.monitor.getId().toString())
                        .param("items[0].quantity", "0")
                        .param("items[0].unitPrice", "300000")
                        .with(user(new LoginUser(f.dev))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("requests/form"))
                .andExpect(content().string(containsString("수량은 1 이상입니다.")));
    }

    @Test
    @DisplayName("다른 직원의 요청서 상세는 403, 품목 관리 메뉴는 직원에게 403")
    void forbidden() throws Exception {
        Long id = f.draft(f.dev, "노트북").getId();
        em.flush();

        mockMvc.perform(get("/requests/" + id).with(user(new LoginUser(f.sales))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("이 구매요청서를 볼 권한이 없습니다.")));
        mockMvc.perform(get("/master/items").with(user(new LoginUser(f.dev))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/master/items").with(user(new LoginUser(f.buyer))))
                .andExpect(status().isOk());
    }
}
