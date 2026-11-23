package com.example.purchase.stats.batch;

import com.example.purchase.master.service.VendorStatusService;
import com.example.purchase.notification.service.ApprovalDelayNotifier;
import com.example.purchase.stats.repository.DeptMonthlyAmount;
import com.example.purchase.stats.repository.MonthlyPurchaseStatRepository;
import com.example.purchase.stats.repository.MonthlyStatQuery;
import jakarta.persistence.EntityManager;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * 매일 밤 배치 (9단계).
 * <pre>
 * nightlyJob      = monthlyStatStep → approvalDelayStep → vendorStatusStep   (스케줄러, 매일 02:00)
 * monthlyStatJob  = monthlyStatStep                                         (통계 화면 [다시 집계] 버튼)
 * </pre>
 *
 * <p>주의: @EnableBatchProcessing을 붙이지 않는다. Spring Boot 3(Batch 5)에서는 이걸 붙이면 Boot 자동 설정이 꺼져서
 * BATCH_ 테이블 생성, spring.batch.* 설정이 모두 무시된다. JobRepository·트랜잭션 매니저는 Boot가 만들어 준다.
 */
@Configuration
public class NightlyBatchConfig {

    public static final String NIGHTLY_JOB = "nightlyJob";
    public static final String MONTHLY_STAT_JOB = "monthlyStatJob";
    public static final String PARAM_YEAR_MONTH = "yearMonth";

    @Bean
    public Job nightlyJob(JobRepository jobRepository, Step monthlyStatStep, Step approvalDelayStep, Step vendorStatusStep) {
        return new JobBuilder(NIGHTLY_JOB, jobRepository)
                .start(monthlyStatStep)
                .next(approvalDelayStep)
                .next(vendorStatusStep)
                .build();
    }

    @Bean
    public Job monthlyStatJob(JobRepository jobRepository, Step monthlyStatStep) {
        return new JobBuilder(MONTHLY_STAT_JOB, jobRepository)
                .start(monthlyStatStep)
                .build();
    }

    /**
     * 1) 월별 통계. chunk(100): 100줄 읽고 → 100줄 쓰고 → 커밋을 반복한다.
     * 부서 수가 적어 지금은 한 번에 끝나지만, 행이 많아져도 메모리와 트랜잭션 크기가 100줄로 묶인다.
     */
    @Bean
    public Step monthlyStatStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                ListItemReader<DeptMonthlyAmount> monthlyStatReader, MonthlyStatWriter monthlyStatWriter) {
        return new StepBuilder("monthlyStatStep", jobRepository)
                .<DeptMonthlyAmount, DeptMonthlyAmount>chunk(100, transactionManager)
                .reader(monthlyStatReader)
                .writer(monthlyStatWriter)
                .build();
    }

    /** @StepScope: step이 실행될 때 만들어서 job 파라미터(yearMonth)를 받을 수 있다 */
    @Bean
    @StepScope
    public ListItemReader<DeptMonthlyAmount> monthlyStatReader(
            @Value("#{jobParameters['" + PARAM_YEAR_MONTH + "']}") String yearMonth, MonthlyStatQuery query) {
        return new ListItemReader<>(query.approvedByDepartment(YearMonth.parse(yearMonth)));
    }

    @Bean
    @StepScope
    public MonthlyStatWriter monthlyStatWriter(
            @Value("#{jobParameters['" + PARAM_YEAR_MONTH + "']}") String yearMonth,
            MonthlyPurchaseStatRepository statRepository, EntityManager em) {
        return new MonthlyStatWriter(yearMonth, statRepository, em);
    }

    /** 2) 결재 지연 알림. DB만 쓰는 짧은 일이라 tasklet 하나로 */
    @Bean
    public Step approvalDelayStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
                                  ApprovalDelayNotifier notifier) {
        return new StepBuilder("approvalDelayStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    int created = notifier.notifyDelayed(LocalDateTime.now());
                    contribution.incrementWriteCount(created);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * 3) 거래처 사업자 상태 갱신 (8단계 국세청 API).
     *
     * <p><b>여기만 ResourcelessTransactionManager를 준다.</b> tasklet은 기본으로 "트랜잭션 안에서" 실행된다.
     * JPA 트랜잭션 매니저를 주면 국세청 응답을 기다리는 내내 DB 커넥션을 붙잡는다 → 8단계에서 고친 커넥션 풀 고갈이
     * 배치에서 그대로 다시 생긴다 (docs/troubleshooting/06, 07). DB 일이 없는 가짜 트랜잭션을 주고,
     * 저장은 VendorService.applyStatus가 자기 짧은 트랜잭션으로 한다.
     * 실행 이력(BATCH_STEP_EXECUTION) 저장은 JobRepository가 따로 하므로 영향이 없다.
     */
    @Bean
    public Step vendorStatusStep(JobRepository jobRepository, VendorStatusService vendorStatusService) {
        return new StepBuilder("vendorStatusStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    VendorStatusService.Summary s = vendorStatusService.refreshAll();
                    contribution.incrementWriteCount(s.checked());
                    contribution.setExitStatus(ExitStatus.COMPLETED.addExitDescription(
                            "전체 %d, 확인 %d, 확인 안 됨 %d, 바뀜 %d%s".formatted(s.total(), s.checked(), s.unknown(),
                                    s.changed(), s.error() == null ? "" : " / " + s.error())));
                    return RepeatStatus.FINISHED;
                }, new ResourcelessTransactionManager())
                .build();
    }
}
