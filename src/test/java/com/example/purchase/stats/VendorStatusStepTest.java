package com.example.purchase.stats;

import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.nts.NtsBusinessClient;
import com.example.purchase.master.service.VendorStatusService;
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
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * 배치에서 국세청 API를 부를 때 DB 커넥션을 붙잡고 있는지 확인한다 (8단계 트러블슈팅 06의 배치 버전).
 * 가짜 국세청 클라이언트가 불리는 순간 "지금 이 스레드에 DB 커넥션이 묶여 있나?"를 기록한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class VendorStatusStepTest {

    @Autowired JobLauncher jobLauncher;
    @Autowired JobRepository jobRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired VendorStatusService vendorStatusService;
    @Autowired @Qualifier("vendorStatusStep") Step vendorStatusStep;
    @Autowired DataSource dataSource;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @MockBean NtsBusinessClient ntsClient;

    final List<Boolean> connectionHeldDuringApiCall = new ArrayList<>();

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(s -> new RequestFixture(em));
        when(ntsClient.isEnabled()).thenReturn(true);
        when(ntsClient.fetchStatuses(anyList())).thenAnswer(inv -> {
            connectionHeldDuringApiCall.add(TransactionSynchronizationManager.hasResource(dataSource));
            List<String> bizNos = inv.getArgument(0);
            return Map.of(bizNos.get(0), new NtsBusinessClient.Result(bizNos.get(0), VendorStatus.ACTIVE, "계속사업자"));
        });
    }

    @AfterEach
    void cleanUp() {
        DbCleaner.clean(jdbc);
    }

    @Test
    @DisplayName("[문제] tasklet에 JPA 트랜잭션 매니저를 주면 API를 기다리는 동안 DB 커넥션이 묶여 있다")
    void taskletWithJpaTransactionHoldsConnection() throws Exception {
        Step step = new StepBuilder("vendorStatusInJpaTx", jobRepository)
                .tasklet((c, ctx) -> {
                    vendorStatusService.refreshAll();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();

        assertThat(run(step).getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(connectionHeldDuringApiCall).containsOnly(true);
    }

    @Test
    @DisplayName("[해결] vendorStatusStep은 ResourcelessTransactionManager라 API를 기다리는 동안 커넥션을 안 잡는다")
    void vendorStatusStepHoldsNoConnection() throws Exception {
        JobExecution e = run(vendorStatusStep);

        assertThat(e.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(connectionHeldDuringApiCall).isNotEmpty().containsOnly(false);
        assertThat(e.getStepExecutions().iterator().next().getExitStatus().getExitDescription()).startsWith("전체 2, 확인 1");   // 가짜 국세청이 첫 번호만 답함
    }

    private JobExecution run(Step step) throws Exception {
        Job job = new JobBuilder("vendorOnly-" + step.getName(), jobRepository).start(step).build();
        return jobLauncher.run(job, new JobParametersBuilder().addLong("run", System.nanoTime()).toJobParameters());
    }
}
