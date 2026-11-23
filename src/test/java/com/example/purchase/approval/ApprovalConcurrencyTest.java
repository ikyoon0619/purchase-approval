package com.example.purchase.approval;

import com.example.purchase.approval.service.ApprovalService;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 같은 문서를 거의 동시에 처리하는 상황 (BR-06, docs/troubleshooting/04).
 *
 * <p>스레드 두 개로 "거의 동시에"를 만들면 실행할 때마다 순서가 달라져 테스트가 가끔만 실패한다.
 * 그래서 순서를 손으로 고정했다:
 * <pre>
 *   트랜잭션 A(팀장): 문서를 읽는다 ─────────────────────────── 승인하고 커밋
 *   트랜잭션 B(기안자):            읽고 → 회수하고 → 커밋
 * </pre>
 * B는 REQUIRES_NEW로 A 안에서 따로 열린 트랜잭션이라, A가 잠깐 멈춘 사이 끝까지 실행되고 커밋된다.
 *
 * <p>테스트 클래스에 @Transactional을 붙이지 않았다. 붙이면 A와 B가 같은 트랜잭션이 되어 충돌이 안 생긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
class ApprovalConcurrencyTest {

    @Autowired ApprovalService approvalService;
    @Autowired PurchaseRequestRepository requestRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;
    TransactionTemplate newTx;
    RequestFixture f;
    LoginUser dev, devLead;
    Long id;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        newTx = new TransactionTemplate(txManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.executeWithoutResult(s -> {
            f = new RequestFixture(em);
            id = f.draft(f.dev, "노트북 구매").getId();
        });
        dev = new LoginUser(f.dev);
        devLead = new LoginUser(f.devLead);
        approvalService.submit(id, dev); // 팀장(PENDING) → 본부장(WAITING)
    }

    @AfterEach
    void cleanUp() {
        for (String table : new String[]{"approval_line", "attachment", "purchase_request_item", "purchase_request", "item", "vendor", "member", "position"}) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from department where parent_id is not null");
        jdbc.update("delete from department");
    }

    @Test
    @DisplayName("[문제] 락 없이 읽으면: 기안자 회수와 팀장 승인이 둘 다 성공해서 '임시저장인데 승인된 결재선'이 남는다")
    void withdrawAndApproveWithoutLock() {
        long[] versionAfterWithdraw = new long[1];
        tx.executeWithoutResult(s -> {
            PurchaseRequest r = requestRepository.findDetail(id).orElseThrow(); // 락 없이 읽기
            r.currentLines();                                                   // 결재선까지 읽어 둠 (팀장 PENDING)

            newTx.executeWithoutResult(s2 -> approvalService.withdraw(id, dev)); // 그사이 기안자가 회수하고 커밋
            versionAfterWithdraw[0] = version();

            r.approve(devLead.getId(), "승인합니다", LocalDateTime.now());         // 팀장은 아까 읽은 상태 기준으로 승인
        }); // 예외 없이 커밋된다!

        assertThat(requestStatus()).isEqualTo("DRAFT");
        assertThat(lineStatuses()).containsExactly("APPROVED", "PENDING"); // 문서는 임시저장인데 본부장 차례?
        assertThat(version()).isEqualTo(versionAfterWithdraw[0]); // 팀장 승인 커밋은 요청서 version을 건드리지 않았다
    }

    @Test
    @DisplayName("[해결] OPTIMISTIC_FORCE_INCREMENT로 읽으면: 나중에 커밋하는 팀장 승인이 실패하고 회수만 반영된다")
    void withdrawAndApproveWithForceIncrement() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            PurchaseRequest r = requestRepository.findForApproval(id).orElseThrow(); // version = 1 로 읽음
            r.currentLines();

            newTx.executeWithoutResult(s2 -> approvalService.withdraw(id, dev));     // version 1 → 2+ 로 커밋

            r.approve(devLead.getId(), "승인합니다", LocalDateTime.now());
            // 커밋할 때: update purchase_request set version=2 where id=? and version=1 → 0건 → 충돌
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(requestStatus()).isEqualTo("DRAFT");
        assertThat(lineStatuses()).containsExactly("CANCELED", "CANCELED");
    }

    @Test
    @DisplayName("[해결] 같은 팀장이 두 탭에서 동시에 승인해도 한 번만 반영된다 (먼저 커밋한 의견이 남음)")
    void doubleApprove() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            PurchaseRequest r = requestRepository.findForApproval(id).orElseThrow();
            r.currentLines();

            newTx.executeWithoutResult(s2 -> approvalService.approve(id, devLead, "첫 번째 탭"));

            r.approve(devLead.getId(), "두 번째 탭", LocalDateTime.now());
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(lineStatuses()).containsExactly("APPROVED", "PENDING");
        assertThat(jdbc.queryForObject("select comment from approval_line where request_id = ? and step_order = 1",
                String.class, id)).isEqualTo("첫 번째 탭");
    }

    private String requestStatus() {
        return jdbc.queryForObject("select status from purchase_request where id = ?", String.class, id);
    }

    private Long version() {
        return jdbc.queryForObject("select version from purchase_request where id = ?", Long.class, id);
    }

    private List<String> lineStatuses() {
        return jdbc.queryForList("select status from approval_line where request_id = ? order by step_order", id)
                .stream().map((Map<String, Object> row) -> (String) row.get("STATUS")).toList();
    }
}
