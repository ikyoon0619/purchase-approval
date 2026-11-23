package com.example.purchase.code;

import com.example.purchase.code.repository.CodeGroupRepository;
import com.example.purchase.code.service.CodeGroupRow;
import com.example.purchase.config.JpaConfig;
import com.example.purchase.config.QuerydslConfig;
import com.example.purchase.security.SecurityAuditorAware;
import com.querydsl.jpa.impl.JPAQueryFactory;
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

import static com.example.purchase.code.domain.QCodeGroup.codeGroup;
import static com.example.purchase.code.domain.QCommonCode.commonCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** [해결] application.yml 설정 그대로 (default_batch_fetch_size: 100, 컬렉션 fetch join 페이징은 오류) */
@DataJpaTest
@ActiveProfiles("test")
@Import({QuerydslConfig.class, JpaConfig.class, SecurityAuditorAware.class})
class CodeGroupPagingTest {

    @Autowired CodeGroupRepository codeGroupRepository;
    @Autowired JPAQueryFactory queryFactory;
    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;

    Statistics stats;

    @BeforeEach
    void setUp() {
        CodeGroupPagingFixture.insert(em);
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
    }

    @Test
    @DisplayName("[해결] 그룹은 limit으로 10개만, 코드는 IN 절 한 번 → 쿼리 3번, 읽은 엔티티 60개")
    void batchFetch() {
        Page<CodeGroupRow> page = codeGroupRepository.search(null, PageRequest.of(0, 10)).map(CodeGroupRow::from);

        long queries = stats.getPrepareStatementCount();
        long loaded = stats.getEntityLoadCount();
        System.out.println("[batch fetch] 그룹 10개 페이지 쿼리 수 = " + queries + ", 읽은 엔티티 수 = " + loaded);
        assertThat(page.getContent()).hasSize(10);
        assertThat(page.getContent().get(0).codeNames()).containsExactly("코드1", "코드2", "코드3", "코드4", "코드5");
        assertThat(page.getTotalElements()).isEqualTo(30);
        assertThat(queries).isEqualTo(3);     // 그룹 10개 + 건수 + 코드(IN)
        assertThat(loaded).isEqualTo(60);     // 그룹 10 + 코드 50
    }

    @Test
    @DisplayName("[예방] 누가 컬렉션 fetch join에 페이징을 붙이면 경고 대신 바로 오류가 난다")
    void collectionFetchJoinWithPagingFails() {
        assertThatThrownBy(() -> queryFactory
                .selectFrom(codeGroup)
                .leftJoin(codeGroup.codes, commonCode).fetchJoin()
                .offset(0)
                .limit(10)
                .fetch())
                .satisfies(e -> System.out.println("[예방] " + e.getClass().getSimpleName() + ": " + e.getMessage()))
                .hasMessageContaining("fail_on_pagination_over_collection_fetch");
    }
}
