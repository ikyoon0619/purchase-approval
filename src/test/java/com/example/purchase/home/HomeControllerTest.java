package com.example.purchase.home;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.PositionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** @WebMvcTest: 컨트롤러와 화면만 띄우고 DB는 가짜(MockBean)로 바꾼다. */
@WebMvcTest(HomeController.class)
class HomeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    DepartmentRepository departmentRepository;

    @MockBean
    PositionRepository positionRepository;

    @Test
    @DisplayName("첫 화면에 부서와 직급이 보인다")
    void home() throws Exception {
        Department division = Department.division("D100", "경영지원본부");
        given(departmentRepository.search(null, true))
                .willReturn(List.of(division, Department.team("D110", "구매팀", division)));
        given(positionRepository.findAllByOrderByRankOrderAsc())
                .willReturn(List.of(new Position("사원", 1), new Position("부장", 5)));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(content().string(containsString("구매팀")))
                .andExpect(content().string(containsString("부장")));
    }

    @Test
    @DisplayName("없는 주소는 404 오류 화면을 보여 준다")
    void notFound() throws Exception {
        mockMvc.perform(get("/no-such-page"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("페이지를 찾을 수 없습니다")));
    }
}
