package com.example.purchase.request;

import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.request.service.RequestDetail;
import com.example.purchase.request.service.RequestService;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * open-in-view: false 에서 생기는 LazyInitializationException을 재현하고, DTO로 해결한 것을 확인한다.
 * (docs/troubleshooting/03-open-in-view-끄고-LazyInitializationException.md)
 *
 * <p>일부러 테스트 클래스에 @Transactional을 붙이지 않았다. 붙이면 테스트 전체가 한 트랜잭션이라 지연 로딩이 항상 성공해서
 * 실제 화면(요청마다 트랜잭션이 끝난 뒤 Thymeleaf가 그리는 상황)을 재현할 수 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LazyInitializationTest {

    @Autowired PurchaseRequestRepository requestRepository;
    @Autowired RequestService requestService;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mockMvc;

    RequestFixture f;
    Long requestId;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> {
            f = new RequestFixture(em);
            requestId = f.draft(f.dev, "노트북 구매").getId();
        });
    }

    @AfterEach
    void cleanUp() {
        // 트랜잭션 롤백으로 지워지지 않으니 직접 비운다 (기본 메뉴·공통코드는 남긴다)
        for (String table : new String[]{"approval_line", "attachment", "purchase_request_item", "purchase_request", "item", "vendor", "member", "position"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from department where parent_id is not null");
        jdbc.update("delete from department");
    }

    @Test
    @DisplayName("[문제] 트랜잭션 밖에서 엔티티의 지연 로딩 필드를 읽으면 LazyInitializationException")
    void entityOutsideTransaction() {
        PurchaseRequest request = requestRepository.findById(requestId).orElseThrow(); // 이 줄이 끝나면 트랜잭션도 끝

        assertThatThrownBy(() -> request.getItems().size())          // 컬렉션
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("failed to lazily initialize a collection");   // ... PurchaseRequest.items: could not initialize proxy - no Session
        assertThatThrownBy(() -> request.getVendor().getName())       // 프록시
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("no Session");                   // could not initialize proxy ... - no Session
    }

    @Test
    @DisplayName("[해결] 서비스가 트랜잭션 안에서 DTO로 바꿔 주면 밖에서 마음껏 읽을 수 있다")
    void dtoOutsideTransaction() {
        RequestDetail detail = requestService.getDetail(requestId, new LoginUser(f.dev));

        assertThat(detail.vendorName()).isEqualTo("(주)오피스몰");
        assertThat(detail.items()).extracting(RequestDetail.Line::itemName).containsExactly("노트북", "모니터", "복사용지");
        assertThat(detail.totalAmount()).isEqualByComparingTo("3967000");
    }

    @Test
    @DisplayName("[실제로 깨졌던 화면] 부서 검색에서 본부가 결과에 없으면 상위 부서 이름을 못 읽었다 → fetch join으로 해결")
    void departmentSearchWithoutParentInResult() throws Exception {
        LoginUser admin = tx.execute(s -> {
            var a = new com.example.purchase.org.domain.Member("admin", "pw", "관리자", null, f.dev.getDepartment(),
                    f.dev.getPosition(), com.example.purchase.org.domain.Role.ADMIN);
            em.persist(a);
            em.flush();
            return new LoginUser(em.createQuery("select m from Member m join fetch m.department where m.loginId = 'admin'",
                    com.example.purchase.org.domain.Member.class).getSingleResult());
        });

        mockMvc.perform(get("/admin/departments").param("keyword", "개발").with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("사업본부")));   // 개발팀의 상위 부서
    }
}
