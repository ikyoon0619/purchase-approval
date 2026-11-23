package com.example.purchase.stats.batch;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * job을 실행하는 곳을 한 군데로 모았다 (스케줄러, 통계 화면 버튼, 로컬 시작 시 집계).
 *
 * <p><b>파라미터 설계가 곧 "몇 번 돌 수 있나"이다.</b> Spring Batch는 (job 이름 + 식별 파라미터)를 JobInstance 하나로 보고,
 * 이미 COMPLETED인 인스턴스를 같은 파라미터로 다시 돌리면 JobInstanceAlreadyCompleteException으로 거절한다.
 * <ul>
 *   <li>매일 밤: yearMonth + runDate(날짜) → 하루에 한 번만. 서버가 두 대여도, 스케줄이 두 번 울려도 두 번 돌지 않는다.
 *       실패(FAILED)했으면 같은 파라미터로 다시 실행 = 재시작이 된다.</li>
 *   <li>[다시 집계] 버튼: yearMonth + requestedAt(누른 시각) → 누를 때마다 새로 돈다. 그래서 writer가 upsert여야 한다.</li>
 * </ul>
 *
 * <p>이 클래스에는 @Transactional을 붙이면 안 된다. JobRepository는 이미 열린 트랜잭션 안에서 job을 시작하면
 * "Existing transaction detected in JobRepository"로 거절한다 (step마다 자기 트랜잭션을 커밋해야 하므로).
 */
@Slf4j
@Component
public class BatchRunner {

    private final JobLauncher jobLauncher;
    private final Job nightlyJob;
    private final Job monthlyStatJob;

    public BatchRunner(JobLauncher jobLauncher,
                       @Qualifier(NightlyBatchConfig.NIGHTLY_JOB) Job nightlyJob,
                       @Qualifier(NightlyBatchConfig.MONTHLY_STAT_JOB) Job monthlyStatJob) {
        this.jobLauncher = jobLauncher;
        this.nightlyJob = nightlyJob;
        this.monthlyStatJob = monthlyStatJob;
    }

    /** 매일 밤: 어제가 속한 달을 집계한다. 1일 새벽에 돌면 지난달이 마지막으로 한 번 더 집계되어 확정된다 */
    public JobExecution runNightly(LocalDate today) {
        JobParameters params = new JobParametersBuilder()
                .addString(NightlyBatchConfig.PARAM_YEAR_MONTH, YearMonth.from(today.minusDays(1)).toString())
                .addLocalDate("runDate", today)
                .toJobParameters();
        return run(nightlyJob, params);
    }

    /** 통계 화면에서 한 달을 다시 집계 */
    @Audited(action = AuditAction.BATCH, target = "월별 통계", id = "#month")
    public JobExecution runMonthlyStat(YearMonth month) {
        if (month.isAfter(YearMonth.now())) {
            throw new BusinessException(ErrorCode.INVALID_MONTH);
        }
        JobParameters params = new JobParametersBuilder()
                .addString(NightlyBatchConfig.PARAM_YEAR_MONTH, month.toString())
                .addLocalDateTime("requestedAt", LocalDateTime.now())
                .toJobParameters();
        return run(monthlyStatJob, params);
    }

    private JobExecution run(Job job, JobParameters params) {
        try {
            JobExecution execution = jobLauncher.run(job, params);   // 기본 JobLauncher는 끝날 때까지 기다린다(동기)
            log.info("배치 {} {} → {}", job.getName(), params, execution.getStatus());
            return execution;
        } catch (JobExecutionAlreadyRunningException e) {
            throw new BusinessException(ErrorCode.BATCH_RUNNING);
        } catch (JobExecutionException e) {
            // JobInstanceAlreadyCompleteException(같은 파라미터로 이미 끝남), JobRestartException 등
            throw new BusinessException(ErrorCode.BATCH_FAILED, e.getMessage());
        }
    }
}
