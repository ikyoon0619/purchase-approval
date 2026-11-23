package com.example.purchase.stats.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import com.example.purchase.org.domain.Department;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 부서·월별 구매 통계 (STA-01). 매일 밤 배치가 채운다. 화면은 요청서 테이블을 매번 합산하지 않고 이 표를 읽는다.
 *
 * <p>UNIQUE(stat_month, department_id): 한 달·한 부서에 한 줄. 배치를 다시 돌리면 새 줄을 넣지 않고 이 줄을 고친다(upsert).
 * 컬럼 이름을 ERD 초안의 year_month가 아니라 stat_month로 바꿨다. YEAR_MONTH는 MySQL 예약어(INTERVAL 단위)다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(name = "uk_monthly_stat_month_dept", columnNames = {"stat_month", "department_id"}))
public class MonthlyPurchaseStat extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "2026-11" */
    @Column(name = "stat_month", nullable = false, length = 7)
    private String statMonth;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    /** 그 달에 최종 승인된 요청서 수 */
    @Column(nullable = false)
    private int requestCount;

    /** 그 달에 최종 승인된 요청서 합계 금액 */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private LocalDateTime aggregatedAt;

    public MonthlyPurchaseStat(String statMonth, Department department, int requestCount, BigDecimal totalAmount,
                               LocalDateTime aggregatedAt) {
        this.statMonth = statMonth;
        this.department = department;
        this.requestCount = requestCount;
        this.totalAmount = totalAmount;
        this.aggregatedAt = aggregatedAt;
    }

    /** 다시 집계: 더하지 않고 새로 센 값으로 바꾼다. 그래야 몇 번을 돌려도 결과가 같다 (멱등) */
    public void replace(int requestCount, BigDecimal totalAmount, LocalDateTime aggregatedAt) {
        this.requestCount = requestCount;
        this.totalAmount = totalAmount;
        this.aggregatedAt = aggregatedAt;
    }
}
