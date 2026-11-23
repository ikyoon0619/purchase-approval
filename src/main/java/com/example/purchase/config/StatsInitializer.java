package com.example.purchase.config;

import com.example.purchase.stats.batch.BatchRunner;
import com.example.purchase.stats.repository.MonthlyPurchaseStatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.YearMonth;

/**
 * 샘플 데이터를 쓸 때(local, 데모 서버): 통계 표가 비어 있으면 최근 6개월을 한 번 집계해 둔다. 새벽 2시까지 기다리지 않고 대시보드 차트를 볼 수 있게.
 * 샘플 데이터(@Order 2) 다음에 돈다. @Transactional을 붙이지 않는다 (BatchRunner 주석 참고).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.sample-data", havingValue = "true")   // local: 항상, prod: 데모 서버만 (SAMPLE_DATA=true)
@Order(4)
@RequiredArgsConstructor
public class StatsInitializer implements ApplicationRunner {

    private final MonthlyPurchaseStatRepository statRepository;
    private final BatchRunner batchRunner;

    @Override
    public void run(ApplicationArguments args) {
        if (statRepository.count() > 0) {
            return;
        }
        YearMonth now = YearMonth.now();
        for (int i = 5; i >= 0; i--) {
            batchRunner.runMonthlyStat(now.minusMonths(i));
        }
        log.info("최근 6개월 구매 통계를 처음으로 집계했습니다.");
    }
}
