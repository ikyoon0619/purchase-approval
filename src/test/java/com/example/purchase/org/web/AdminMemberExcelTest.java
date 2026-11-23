package com.example.purchase.org.web;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.OrgFixture;
import jakarta.persistence.EntityManager;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 사용자 목록 페이징 화면과 엑셀 다운로드 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminMemberExcelTest {

    @Autowired MockMvc mockMvc;
    @Autowired EntityManager em;

    Member admin;

    @BeforeEach
    void setUp() {
        OrgFixture f = new OrgFixture(em);
        Department dev = f.team("D210", "개발팀", f.division("D200", "사업본부"));
        Department sales = f.team("D220", "영업팀", f.division("D300", "영업본부"));
        Position p = f.position("사원", 1);
        admin = f.member("admin", "관리자", dev, p, Role.ADMIN);
        for (int i = 1; i <= 14; i++) {
            f.member(String.format("emp%02d", i), "직원" + i, i % 2 == 0 ? dev : sales, p, Role.EMPLOYEE);
        }
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("사용자 목록은 10명씩 나뉘고, 2페이지에는 나머지 5명이 보인다")
    void paging() throws Exception {
        mockMvc.perform(get("/admin/members").param("page", "2").with(user(new LoginUser(admin))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("총 <strong>15</strong>명")))
                .andExpect(content().string(containsString("<span>2</span>/<span>2</span> 페이지")));
    }

    @Test
    @DisplayName("엑셀: 검색 조건(영업팀)이 그대로 적용되고, 한글 파일명이 UTF-8로 인코딩된다")
    void excel() throws Exception {
        Long salesId = em.createQuery("select d.id from Department d where d.code = 'D220'", Long.class).getSingleResult();

        MockHttpServletResponse response = mockMvc.perform(get("/admin/members/excel")
                        .param("departmentId", salesId.toString())
                        .with(user(new LoginUser(admin))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''%EC%82%AC%EC%9A%A9%EC%9E%90")))
                .andReturn().getResponse();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("사용자");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("아이디");
            assertThat(sheet.getLastRowNum()).isEqualTo(7);                         // 머리글 + 영업팀 7명
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("영업팀");
        }
    }

    @Test
    @DisplayName("직원은 엑셀도 받을 수 없다 (/admin/** 는 관리자만)")
    void excelForbiddenForEmployee() throws Exception {
        Member emp = em.createQuery("select m from Member m join fetch m.department where m.loginId = 'emp01'", Member.class)
                .getSingleResult();
        mockMvc.perform(get("/admin/members/excel").with(user(new LoginUser(emp))))
                .andExpect(status().isForbidden());
    }
}
