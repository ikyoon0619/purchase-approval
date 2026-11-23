package com.example.purchase.stats.batch;

import com.example.purchase.org.domain.Department;
import com.example.purchase.stats.domain.MonthlyPurchaseStat;
import com.example.purchase.stats.repository.DeptMonthlyAmount;
import com.example.purchase.stats.repository.MonthlyPurchaseStatRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.time.LocalDateTime;

/**
 * 통계 표에 쓴다. <b>있으면 고치고, 없으면 넣는다 (upsert).</b>
 * 처음엔 그냥 save(new ...)로 넣었더니, 같은 달을 다시 집계할 때 UNIQUE(stat_month, department_id) 위반으로
 * step이 FAILED 됐다. UNIQUE가 없었다면 줄이 두 개가 되어 대시보드 금액이 두 배로 보였을 것이다 (docs/troubleshooting/07).
 */
@RequiredArgsConstructor
public class MonthlyStatWriter implements ItemWriter<DeptMonthlyAmount> {

    private final String statMonth;
    private final MonthlyPurchaseStatRepository statRepository;
    private final EntityManager em;

    @Override
    public void write(Chunk<? extends DeptMonthlyAmount> chunk) {
        LocalDateTime now = LocalDateTime.now();
        for (DeptMonthlyAmount row : chunk) {
            int count = row.requestCount().intValue();
            statRepository.findByStatMonthAndDepartmentId(statMonth, row.departmentId())
                    .ifPresentOrElse(
                            stat -> stat.replace(count, row.totalAmount(), now),
                            () -> statRepository.save(new MonthlyPurchaseStat(statMonth,
                                    em.getReference(Department.class, row.departmentId()),   // select 없이 FK만 채운다
                                    count, row.totalAmount(), now)));
        }
    }
}
