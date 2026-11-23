package com.example.purchase.org.repository;

import com.example.purchase.config.JpaConfig;
import com.example.purchase.config.QuerydslConfig;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.security.SecurityAuditorAware;
import com.example.purchase.support.OrgFixture;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 문제를 테스트로 재현하고, fetch join으로 고친 결과를 쿼리 수로 비교한다.
 * (docs/troubleshooting/01-사용자-목록-N+1.md)
 *
 * <p>4단계부터 application.yml에 default_batch_fetch_size: 100 이 켜져 있어서,
 * 순수한 N+1을 재현하려고 이 테스트에서만 1로 끈다. (켜 둔 결과는 CodeGroupPagingTest에서 비교)
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.default_batch_fetch_size=1")
@ActiveProfiles("test")
@Import({QuerydslConfig.class, JpaConfig.class, SecurityAuditorAware.class})
class MemberRepositoryTest {

    @Autowired MemberRepository memberRepository;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    Statistics stats;

    /** 사용자 10명을 팀 5곳, 직급 5개에 골고루 나눠 넣는다 */
    @BeforeEach
    void setUp() {
        OrgFixture f = new OrgFixture(em);
        Department division = f.division("D100", "본부");
        Department[] teams = new Department[5];
        Position[] positions = new Position[5];
        for (int i = 0; i < 5; i++) {
            teams[i] = f.team("D1" + (i + 1) + "0", "팀" + (i + 1), division);
            positions[i] = f.position("직급" + (i + 1), i + 1);
        }
        for (int i = 0; i < 10; i++) {
            f.member("user" + i, "사용자" + i, teams[i % 5], positions[i % 5], Role.EMPLOYEE);
        }
        em.flush();
        em.clear(); // 1차 캐시를 비워야 실제로 DB에서 읽는 쿼리 수를 셀 수 있다

        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    @DisplayName("[문제] findAll() 후 부서·직급 이름을 읽으면 사용자 1번 + 부서 5번 + 직급 5번 = 11번 조회한다")
    void nPlusOne() {
        List<Member> members = memberRepository.findAll();           // 쿼리 1번: 사용자 10명
        for (Member m : members) {
            m.getDepartment().getName();                               // 처음 보는 부서마다 쿼리 1번
            m.getPosition().getName();                                 // 처음 보는 직급마다 쿼리 1번
        }

        long queries = stats.getPrepareStatementCount();
        System.out.println("[N+1] 사용자 " + members.size() + "명 목록 쿼리 수 = " + queries);
        assertThat(queries).isEqualTo(11);
    }

    @Test
    @DisplayName("[해결] fetch join으로 읽으면 쿼리 1번으로 끝난다")
    void fetchJoin() {
        List<Member> members = memberRepository.searchAll(MemberSearchCond.empty());
        for (Member m : members) {
            m.getDepartment().getName();
            m.getPosition().getName();
        }

        long queries = stats.getPrepareStatementCount();
        System.out.println("[fetch join] 사용자 " + members.size() + "명 목록 쿼리 수 = " + queries);
        assertThat(members).hasSize(10);
        assertThat(queries).isEqualTo(1);
    }

    @Test
    @DisplayName("이름·아이디 검색과 역할 조건을 함께 쓴다")
    void searchWithCondition() {
        List<Member> byKeyword = memberRepository.searchAll(new MemberSearchCond("사용자1", null, null));
        List<Member> byRole = memberRepository.searchAll(new MemberSearchCond(null, null, Role.ADMIN));

        assertThat(byKeyword).extracting(Member::getLoginId).containsExactly("user1");
        assertThat(byRole).isEmpty();
    }

    @Test
    @DisplayName("페이징: ManyToOne fetch join은 DB에서 limit으로 자르고, 내용 1번 + 건수 1번 = 쿼리 2번")
    void paging() {
        Page<Member> page = memberRepository.search(MemberSearchCond.empty(), PageRequest.of(1, 4)); // 두 번째 페이지

        page.getContent().forEach(m -> m.getDepartment().getName());

        assertThat(page.getContent()).hasSize(4);
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("마지막 페이지에서 내용이 페이지 크기보다 적으면 건수 쿼리를 생략한다 (PageableExecutionUtils)")
    void pagingSkipsCountQuery() {
        Page<Member> page = memberRepository.search(MemberSearchCond.empty(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1);
    }
}
