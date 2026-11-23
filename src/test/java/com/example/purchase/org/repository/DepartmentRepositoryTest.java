package com.example.purchase.org.repository;

import com.example.purchase.config.JpaConfig;
import com.example.purchase.config.QuerydslConfig;
import com.example.purchase.org.domain.Department;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest: JPA 관련 빈만 띄우는 가벼운 테스트. 테스트마다 끝나면 롤백된다.
 * QueryDSL 공장과 Auditing 설정은 자동으로 안 올라오므로 @Import로 넣어 준다.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({QuerydslConfig.class, JpaConfig.class})
class DepartmentRepositoryTest {

    @Autowired
    DepartmentRepository departmentRepository;

    @BeforeEach
    void setUp() {
        Department support = departmentRepository.save(Department.division("D100", "경영지원본부"));
        Department business = departmentRepository.save(Department.division("D200", "사업본부"));
        departmentRepository.save(Department.team("D110", "구매팀", support));
        departmentRepository.save(Department.team("D210", "개발팀", business));
    }

    @Test
    @DisplayName("검색 조건이 모두 null이면 전체를 코드 순으로 돌려준다")
    void search_noCondition() {
        List<Department> result = departmentRepository.search(null, null);

        assertThat(result).extracting(Department::getCode)
                .containsExactly("D100", "D110", "D200", "D210");
    }

    @Test
    @DisplayName("이름 일부로 검색한다")
    void search_byKeyword() {
        List<Department> result = departmentRepository.search("본부", null);

        assertThat(result).extracting(Department::getName)
                .containsExactly("경영지원본부", "사업본부");
    }

    @Test
    @DisplayName("팀은 상위 본부를 가진다")
    void team_hasDivision() {
        Department team = departmentRepository.findByCode("D110").orElseThrow();

        assertThat(team.isDivision()).isFalse();
        assertThat(team.getParent().getName()).isEqualTo("경영지원본부");
    }

    @Test
    @DisplayName("저장하면 생성 시각과 작성자가 자동으로 채워진다")
    void auditing() {
        Department saved = departmentRepository.findByCode("D100").orElseThrow();

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getCreatedBy()).isEqualTo("system");
    }
}
