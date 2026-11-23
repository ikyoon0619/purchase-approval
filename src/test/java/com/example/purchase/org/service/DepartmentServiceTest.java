package com.example.purchase.org.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.web.DepartmentForm;
import com.example.purchase.support.OrgFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DepartmentServiceTest {

    @Autowired DepartmentService departmentService;
    @Autowired EntityManager em;

    @Test
    @DisplayName("상위 부서를 고르면 팀으로, 비우면 본부로 등록된다")
    void create() {
        Department division = new OrgFixture(em).division("D100", "경영지원본부");
        DepartmentForm form = new DepartmentForm();
        form.setCode("D130");
        form.setName("총무팀");
        form.setParentId(division.getId());

        Long id = departmentService.create(form);

        Department saved = departmentService.get(id);
        assertThat(saved.isDivision()).isFalse();
        assertThat(saved.getParent().getName()).isEqualTo("경영지원본부");
    }

    @Test
    @DisplayName("팀을 상위 부서로 지정하면 업무 예외 (본부 > 팀 2단 구조만 허용)")
    void update_parentMustBeDivision() {
        OrgFixture f = new OrgFixture(em);
        Department division = f.division("D100", "경영지원본부");
        Department teamA = f.team("D110", "구매팀", division);
        Department teamB = f.team("D120", "인사팀", division);

        DepartmentForm form = DepartmentForm.from(teamB);
        form.setParentId(teamA.getId());

        assertThatThrownBy(() -> departmentService.update(teamB.getId(), form))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_PARENT_DEPARTMENT);
    }

    @Test
    @DisplayName("이미 있는 부서 코드로 등록하면 업무 예외")
    void create_duplicateCode() {
        new OrgFixture(em).division("D100", "경영지원본부");
        DepartmentForm form = new DepartmentForm();
        form.setCode("D100");
        form.setName("중복");

        assertThatThrownBy(() -> departmentService.create(form))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_DEPARTMENT_CODE);
    }
}
