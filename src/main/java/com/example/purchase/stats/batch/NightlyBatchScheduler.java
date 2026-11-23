package com.example.purchase.stats.batch;

import com.example.purchase.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 매일 02:00(한국 시간)에 nightlyJob을 돌린다. 테스트에서는 app.batch.scheduling-enabled=false라 이 빈이 없다.
 *
 * <p>서버를 여러 대 띄우면 각 서버의 스케줄러가 모두 울린다. 파라미터에 runDate가 있어서 두 번째는
 * "이미 완료"로 거절되지만, 운영에서는 ShedLock 같은 분산 락이나 별도 배치 서버를 두는 게 정석이다.
 * 이 프로젝트는 EC2 한 대에 배포하므로 여기까지만 한다.
 */
@Slf4j
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.batch.scheduling-enabled", havingValue = "true")
@RequiredArgsConstructor
public class NightlyBatchScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BatchRunner batchRunner;

    @Scheduled(cron = "${app.batch.nightly-cron}", zone = "Asia/Seoul")
    public void runNightly() {
        try {
            batchRunner.runNightly(LocalDate.now(SEOUL));
        } catch (BusinessException e) {
            // 스케줄러 스레드에서 던진 예외는 아무도 받지 않는다. 로그로 남긴다 (10단계에서 에러 로그 테이블로)
            log.error("야간 배치 실행 실패: {}", e.getMessage());
        }
    }
}
