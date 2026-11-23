package com.example.purchase.stats;

import com.example.purchase.approval.service.ApprovalLineResolver;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.notification.service.ApprovalDelayNotifier;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.RequestStatus;
import com.example.purchase.stats.batch.BatchRunner;
import com.example.purchase.stats.batch.NightlyBatchConfig;
import com.example.purchase.stats.domain.MonthlyPurchaseStat;
import com.example.purchase.stats.repository.DeptMonthlyAmount;
import com.example.purchase.stats.repository.MonthlyPurchaseStatRepository;
import com.example.purchase.stats.repository.MonthlyStatQuery;
import com.example.purchase.support.DbCleaner;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.*;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 9단계 배치. @Transactional을 붙이지 않았다: 붙이면 job을 시작하는 순간
 * "Existing transaction detected in JobRepository"로 실패한다 (아래 마지막 테스트가 그걸 보여 준다).
 * 그래서 데이터는 TransactionTemplate으로 커밋하고, 끝나면 DbCleaner로 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class NightlyBatchTest {

    @Autowired BatchRunner batchRunner;
    @Autowired JobLauncher jobLauncher;
    @Autowired JobRepository jobRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired @Qualifier(NightlyBatchConfig.MONTHLY_STAT_JOB) Job monthlyStatJob;
    @Autowired MonthlyStatQuery statQuery;
    @Autowired MonthlyPurchaseStatRepository statRepository;
    @Autowired ApprovalDelayNotifier delayNotifier;
    @Autowired ApprovalLineResolver resolver;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;

    RequestFixture f;
    final YearMonth thisMonth = YearMonth.now();
    final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> f = new RequestFixture(em));
    }

    @AfterEach
    void cleanUp() {
        DbCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("월별 통계: 그 달에 최종 승인된 요청만 기안 부서별로 합친다 (반려·결재중·임시저장·다른 달 제외)")
    void aggregatesApprovedByDepartment() {
        tx.executeWithoutResult(s -> {
            approved(f.dev, "노트북 세트", now);                                  // 3,967,000
            approved(f.dev, "노트북 1대", now, 1);                                // 1,650,000
            approved(f.sales, "영업 노트북 세트", now);                           // 3,967,000
            approved(f.dev, "지난달 문서", now.minusMonths(1));                   // 다른 달
            PurchaseRequest rejected = submitted(f.dev, "반려 문서", now);
            rejected.reject(f.devLead.getId(), "예산 초과", now);
            submitted(f.dev, "결재중 문서", now);
            f.draft(f.dev, "임시저장 문서");
        });

        JobExecution e = batchRunner.runMonthlyStat(thisMonth);

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(rows(thisMonth)).containsExactlyInAnyOrder("개발팀 2건 5617000원", "영업팀 1건 3967000원");
    }

    @Test
    @DisplayName("[문제1] 같은 파라미터로 다시 돌리면 Spring Batch가 '이미 완료된 인스턴스'라며 거절한다")
    void sameParametersRejected() throws Exception {
        JobParameters params = new JobParametersBuilder()
                .addString(NightlyBatchConfig.PARAM_YEAR_MONTH, thisMonth.toString())
                .addLocalDateTime("requestedAt", LocalDateTime.of(2000, 1, 1, 0, 0))   // 일부러 고정
                .toJobParameters();
        assertThat(jobLauncher.run(monthlyStatJob, params).getStatus()).isEqualTo(BatchStatus.COMPLETED);

        assertThatThrownBy(() -> jobLauncher.run(monthlyStatJob, params))
                .isInstanceOf(JobInstanceAlreadyCompleteException.class)
                .hasMessageContaining("already exists and is complete");
    }

    @Test
    @DisplayName("[문제2] 파라미터를 바꿔 다시 돌려도, writer가 그냥 insert하면 UNIQUE(월, 부서) 위반으로 FAILED")
    void naiveInsertWriterFailsOnRerun() throws Exception {
        tx.executeWithoutResult(s -> approved(f.dev, "노트북 세트", now));
        assertThat(jobLauncher.run(naiveJob(), newParams()).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        JobExecution second = jobLauncher.run(naiveJob(), newParams());

        assertThat(second.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(second.getAllFailureExceptions()).first().isInstanceOf(DataIntegrityViolationException.class);
        assertThat(statRepository.count()).isEqualTo(1);   // 두 번째 실행은 롤백
    }

    @Test
    @DisplayName("[해결] 다시 집계는 누를 때마다 새로 돌고(requestedAt), upsert라서 한 줄이 그대로 고쳐진다")
    void rerunIsIdempotent() {
        tx.executeWithoutResult(s -> approved(f.dev, "노트북 세트", now));
        assertThat(batchRunner.runMonthlyStat(thisMonth).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(batchRunner.runMonthlyStat(thisMonth).getStatus()).isEqualTo(BatchStatus.COMPLETED);   // 같은 결과
        assertThat(rows(thisMonth)).containsExactly("개발팀 1건 3967000원");

        tx.executeWithoutResult(s -> approved(f.dev, "추가 승인", now, 1));   // 그 사이 승인 1건 더
        batchRunner.runMonthlyStat(thisMonth);

        assertThat(rows(thisMonth)).containsExactly("개발팀 2건 5617000원");
        Long count = jdbc.queryForObject("select count(*) from monthly_purchase_stat", Long.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("결재 지연 알림: 3일 넘게 기다린 결재자에게만 한 번. 밤 배치는 하루 한 번만 돈다")
    void approvalDelayNotice() {
        tx.executeWithoutResult(s -> {
            submitted(f.dev, "4일째 대기", now.minusDays(4));
            submitted(f.sales, "어제 상신", now.minusDays(1));
        });

        JobExecution e = batchRunner.runNightly(LocalDate.now());

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(e.getStepExecutions()).extracting(StepExecution::getStepName)
                .containsExactly("monthlyStatStep", "approvalDelayStep", "vendorStatusStep");
        List<String> messages = jdbc.queryForList(
                "select n.message from notification n join member m on m.id = n.member_id where m.login_id = 'devlead'", String.class);
        assertThat(messages).singleElement().asString().contains("4일째 대기").contains("4일째 기다리고");
        Long total = jdbc.queryForObject("select count(*) from notification", Long.class);
        assertThat(total).isEqualTo(1);   // 어제 상신한 건은 아직 아님

        // 같은 날 또 돌리면 Batch가 거절 (runDate가 같은 인스턴스), 알림 로직을 직접 다시 불러도 중복 없음 (dedupKey)
        assertThatThrownBy(() -> batchRunner.runNightly(LocalDate.now())).isInstanceOf(BusinessException.class);
        Integer again = tx.execute(s -> delayNotifier.notifyDelayed(LocalDateTime.now()));
        assertThat(again).isZero();
    }

    @Test
    @DisplayName("[막힌 것] @Transactional 안에서 job을 시작하면 JobRepository가 거절한다")
    void cannotLaunchInsideTransaction() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> batchRunner.runMonthlyStat(thisMonth)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Existing transaction detected in JobRepository");
    }

    // ---------------------------------------------------------------

    /** 처음 만들었던 writer: 그냥 insert. (ListItemReader는 읽은 항목을 지우므로 실행마다 새로 만든다) */
    private Job naiveJob() {
        Step step = new StepBuilder("naiveMonthlyStatStep", jobRepository)
                .<DeptMonthlyAmount, DeptMonthlyAmount>chunk(100, transactionManager)
                .reader(new ListItemReader<>(statQuery.approvedByDepartment(thisMonth)))
                .writer(chunk -> chunk.forEach(row -> statRepository.save(new MonthlyPurchaseStat(thisMonth.toString(),
                        em.getReference(Department.class, row.departmentId()),
                        row.requestCount().intValue(), row.totalAmount(), LocalDateTime.now()))))
                .build();
        return new JobBuilder("naiveMonthlyStatJob", jobRepository).start(step).build();
    }

    private JobParameters newParams() {
        return new JobParametersBuilder()
                .addString(NightlyBatchConfig.PARAM_YEAR_MONTH, thisMonth.toString())
                .addLong("run", System.nanoTime())
                .toJobParameters();
    }

    private List<String> rows(YearMonth month) {
        return jdbc.queryForList("""
                select concat(d.name, ' ', s.request_count, '건 ', cast(s.total_amount as bigint), '원')
                  from monthly_purchase_stat s join department d on d.id = s.department_id
                 where s.stat_month = ?
                """, String.class, month.toString());
    }

    private PurchaseRequest submitted(Member requester, String title, LocalDateTime at) {
        PurchaseRequest r = f.draft(requester, title);
        r.submit(resolver.resolve(r), at);
        return r;
    }

    private void approved(Member requester, String title, LocalDateTime at) {
        approveAll(submitted(requester, title, at.minusHours(1)), at);
    }

    private void approved(Member requester, String title, LocalDateTime at, int laptops) {
        PurchaseRequest r = f.draftLaptops(requester, title, laptops);
        r.submit(resolver.resolve(r), at.minusHours(1));
        approveAll(r, at);
    }

    private void approveAll(PurchaseRequest r, LocalDateTime at) {
        while (r.getStatus() == RequestStatus.IN_APPROVAL) {
            r.approve(r.pendingLine().orElseThrow().getApprover().getId(), null, at);
        }
    }
}
