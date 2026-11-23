package com.example.purchase.code;

import com.example.purchase.code.domain.CodeGroup;
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

import java.util.List;

import static com.example.purchase.code.domain.QCodeGroup.codeGroup;
import static com.example.purchase.code.domain.QCommonCode.commonCode;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * [문제 재현] 4단계에서 켠 두 설정을 이 테스트에서만 끄고, 설정 전의 문제 두 가지를 숫자로 남긴다.
 * (docs/troubleshooting/02-공통코드-페이징-컬렉션-fetch-join.md)
 * <ul>
 *   <li>default_batch_fetch_size 끔 → 그룹마다 코드를 따로 읽는 N+1</li>
 *   <li>fail_on_pagination_over_collection_fetch 끔 → 컬렉션 fetch join + 페이징이 경고만 찍고 전체를 메모리로</li>
 * </ul>
 */
@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.default_batch_fetch_size=1",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=false"
})
@ActiveProfiles("test")
@Import({QuerydslConfig.class, JpaConfig.class, SecurityAuditorAware.class})
class CodeGroupPagingProblemTest {

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
    @DisplayName("[문제 1] 그룹 10개 페이지 + 코드 지연 로딩 = 내용 1 + 건수 1 + 그룹마다 1(10) = 쿼리 12번")
    void nPlusOneOnCollection() {
        Page<CodeGroupRow> page = codeGroupRepository.search(null, PageRequest.of(0, 10)).map(CodeGroupRow::from);

        long queries = stats.getPrepareStatementCount();
        System.out.println("[배치 없음] 그룹 10개 페이지 쿼리 수 = " + queries);
        assertThat(page.getContent()).hasSize(10);
        assertThat(queries).isEqualTo(12);
    }

    @Test
    @DisplayName("[문제 2] 컬렉션 fetch join + limit 10 → SQL에 limit이 없다. 180개를 다 읽어서 메모리에서 10개로 자른다")
    void collectionFetchJoinWithPaging() {
        List<CodeGroup> groups = queryFactory
                .selectFrom(codeGroup)
                .leftJoin(codeGroup.codes, commonCode).fetchJoin()
                .orderBy(codeGroup.groupCode.asc())
                .offset(0)
                .limit(10)
                .fetch();   // 로그: HHH90003004: firstResult/maxResults specified with collection fetch; applying in memory

        long loaded = stats.getEntityLoadCount();
        System.out.println("[컬렉션 fetch join + 페이징] 화면에 쓸 그룹 " + groups.size() + "개를 위해 DB에서 읽은 엔티티 수 = " + loaded);
        assertThat(groups).hasSize(10);
        assertThat(loaded).isEqualTo(CodeGroupPagingFixture.GROUPS
                + CodeGroupPagingFixture.GROUPS * CodeGroupPagingFixture.CODES_PER_GROUP); // 30 + 150 = 180
    }
}
