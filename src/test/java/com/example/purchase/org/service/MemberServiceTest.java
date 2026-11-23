package com.example.purchase.org.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.web.MemberForm;
import com.example.purchase.support.OrgFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberServiceTest {

    @Autowired MemberService memberService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired EntityManager em;

    Department team;
    Position position;

    @BeforeEach
    void setUp() {
        OrgFixture f = new OrgFixture(em);
        team = f.team("D210", "개발팀", f.division("D200", "사업본부"));
        position = f.position("사원", 1);
    }

    private MemberForm form(String loginId, String password) {
        MemberForm form = new MemberForm();
        form.setLoginId(loginId);
        form.setPassword(password);
        form.setName("김개발");
        form.setDepartmentId(team.getId());
        form.setPositionId(position.getId());
        form.setRole(Role.EMPLOYEE);
        return form;
    }

    @Test
    @DisplayName("등록하면 비밀번호를 BCrypt로 바꿔 저장한다 (평문 저장 금지)")
    void create_encodesPassword() {
        Long id = memberService.create(form("dev1", "abcd1234"));

        Member saved = memberService.get(id);
        assertThat(saved.getPassword()).isNotEqualTo("abcd1234").startsWith("$2a$");
        assertThat(passwordEncoder.matches("abcd1234", saved.getPassword())).isTrue();
    }

    @Test
    @DisplayName("같은 아이디로 두 번 등록하면 업무 예외")
    void create_duplicateLoginId() {
        memberService.create(form("dev1", "abcd1234"));

        assertThatThrownBy(() -> memberService.create(form("dev1", "abcd1234")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
    }

    @Test
    @DisplayName("수정할 때 비밀번호를 비워 두면 기존 비밀번호를 유지한다")
    void update_keepsPasswordWhenBlank() {
        Long id = memberService.create(form("dev1", "abcd1234"));
        String before = memberService.get(id).getPassword();

        MemberForm edit = form("dev1", "");
        edit.setName("김개발2");
        memberService.update(id, edit);
        em.flush();
        em.clear();

        Member after = memberService.get(id);
        assertThat(after.getName()).isEqualTo("김개발2");
        assertThat(after.getPassword()).isEqualTo(before);
    }
}
