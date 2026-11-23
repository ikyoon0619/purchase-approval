package com.example.purchase.audit;

import com.example.purchase.approval.service.ApprovalService;
import com.example.purchase.audit.domain.AuditLog;
import com.example.purchase.audit.repository.AuditLogRepository;
import com.example.purchase.audit.service.AuditLogWriter;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.security.LoginUser;
import com.example.purchase.support.DbCleaner;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 감사 로그 AOP (OPS-01, docs/troubleshooting/08).
 * 감사 로그는 업무 트랜잭션과 따로 커밋되므로 테스트도 @Transactional 없이 실제로 커밋하고 DbCleaner로 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AuditAspectTest.SelfCallService.class)
class AuditAspectTest {

    @Autowired ApprovalService approvalService;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired AuditLogWriter writer;
    @Autowired SelfCallService selfCallService;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;
    TransactionTemplate newTx;
    RequestFixture f;
    LoginUser dev, devLead, sales;
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
        sales = new LoginUser(f.sales);
    }

    @AfterEach
    void cleanUp() {
        DbCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("상신·승인하면 누가, 무엇을, 어느 문서에 했는지 성공으로 남는다 (서비스에는 @Audited 한 줄)")
    void successIsRecorded() {
        approvalService.submit(id, dev);
        approvalService.approve(id, devLead, "확인");

        assertThat(logs()).containsExactly(
                "dev1 SUBMIT 구매요청서#" + id + " 성공",
                "devlead APPROVE 구매요청서#" + id + " 성공");
    }

    @Test
    @DisplayName("[문제1] 업무 트랜잭션 안에서 그냥 저장하면, 업무가 실패해 롤백될 때 '실패 기록'도 같이 사라진다")
    void failureLogRolledBackWithBusiness() {
        approvalService.submit(id, dev);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            // 처음 만든 방식: 같은 트랜잭션에 기록하고 업무를 진행
            auditLogRepository.save(new AuditLog(sales.getId(), "sales1", AuditAction.APPROVE, "구매요청서",
                    String.valueOf(id), false, "처음 방식", null, LocalDateTime.now()));
            approvalService.approve(id, sales, "남의 문서 승인 시도");   // 차례가 아님 → 예외 → 전체 롤백
        })).isInstanceOf(BusinessException.class);

        // 처음 방식의 기록은 롤백으로 사라졌고, AuditAspect가 REQUIRES_NEW로 쓴 실패 기록만 남았다
        assertThat(jdbc.queryForObject("select count(*) from audit_log where detail = '처음 방식'", Long.class)).isZero();
        assertThat(logs()).containsExactly(
                "dev1 SUBMIT 구매요청서#" + id + " 성공",
                "sales1 APPROVE 구매요청서#" + id + " 실패");
    }

    @Test
    @DisplayName("[문제2→해결] 커밋할 때 실패한 승인(동시 처리 충돌): 바로 SUCCESS를 쓰면 성공으로 남고, 커밋 뒤에 쓰면 실패로 남는다")
    void commitFailureIsNotSuccess() {
        approvalService.submit(id, dev);

        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            approvalService.approve(id, devLead, "두 번째 탭");   // 아직 커밋 전 (바깥 트랜잭션)
            // 처음 만든 방식: 메서드가 예외 없이 돌아왔으니 바로 "성공"
            writer.write(new AuditLog(devLead.getId(), "devlead", AuditAction.APPROVE, "구매요청서",
                    String.valueOf(id), true, "처음 방식", null, LocalDateTime.now()));
            newTx.executeWithoutResult(s2 -> approvalService.approve(id, devLead, "첫 번째 탭"));   // 다른 탭이 먼저 커밋
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);   // 두 번째 탭은 커밋할 때 충돌

        Boolean firstVersion = jdbc.queryForObject("select success from audit_log where detail = '처음 방식'", Boolean.class);
        assertThat(firstVersion).isTrue();   // [문제2] 실제로는 반영 안 됐는데 "성공"

        // [해결] AuditAspect는 바깥 트랜잭션이 끝난 뒤(afterCompletion) 결과를 보고 쓴다
        assertThat(jdbc.queryForList("""
                select concat(login_id, ' ', success, ' ', coalesce(detail, '')) from audit_log
                 where action = 'APPROVE' and detail is distinct from '처음 방식' order by id
                """, String.class))
                .containsExactly("devlead TRUE ", "devlead FALSE 트랜잭션이 커밋되지 않았습니다 (롤백)");
        assertThat(jdbc.queryForObject("select comment from approval_line where request_id = ? and step_order = 1",
                String.class, id)).isEqualTo("첫 번째 탭");
    }

    @Test
    @DisplayName("[함정] 같은 객체 안에서 this.method()로 부르면 @Audited가 동작하지 않는다 (프록시를 안 거침)")
    void selfInvocationIsNotAudited() {
        selfCallService.outer();          // 안에서 this.inner()
        assertThat(auditLogRepository.count()).isZero();

        selfCallService.inner();          // 프록시를 거쳐 직접
        assertThat(auditLogRepository.count()).isEqualTo(1);
    }

    private List<String> logs() {
        return auditLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AuditLog::getId))
                .map(l -> "%s %s %s#%s %s".formatted(l.getLoginId(), l.getAction(), l.getTargetType(), l.getTargetId(),
                        l.isSuccess() ? "성공" : "실패"))
                .toList();
    }

    @TestConfiguration
    @Service
    static class SelfCallService {

        public void outer() {
            inner();   // = this.inner(): 프록시가 아니라 원본 객체의 메서드를 바로 부른다
        }

        @Audited(action = AuditAction.UPDATE, target = "테스트")
        public void inner() {
        }
    }
}
