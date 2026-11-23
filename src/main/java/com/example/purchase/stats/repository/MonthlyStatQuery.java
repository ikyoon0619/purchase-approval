package com.example.purchase.stats.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.YearMonth;
import java.util.List;

/**
 * 한 달 동안 최종 승인된 요청서를 기안 당시 부서별로 합산한다.
 * - 기준 시각: completed_at (마지막 결재자가 승인한 시각). 그 뒤 발주·입고돼도 completed_at은 그대로라 같은 달에 남는다.
 * - 부서: 요청서에 저장된 기안 당시 부서. 기안자가 나중에 부서를 옮겨도 옛 통계가 바뀌지 않는다 (docs/02-ERD.md)
 * - 반려·결재중·임시저장은 뺀다.
 * DB에서 group by로 줄여서 가져오므로 요청서가 수만 건이어도 결과는 부서 수만큼이다.
 */
@Repository
@RequiredArgsConstructor
public class MonthlyStatQuery {

    private final EntityManager em;

    public List<DeptMonthlyAmount> approvedByDepartment(YearMonth month) {
        return em.createQuery("""
                        select new com.example.purchase.stats.repository.DeptMonthlyAmount(
                                 r.department.id, count(r), sum(r.totalAmount))
                          from PurchaseRequest r
                         where r.status in (com.example.purchase.request.domain.RequestStatus.APPROVED,
                                            com.example.purchase.request.domain.RequestStatus.ORDERED,
                                            com.example.purchase.request.domain.RequestStatus.RECEIVED)
                           and r.completedAt >= :from and r.completedAt < :to
                         group by r.department.id
                         order by r.department.id
                        """, DeptMonthlyAmount.class)
                .setParameter("from", month.atDay(1).atStartOfDay())
                .setParameter("to", month.plusMonths(1).atDay(1).atStartOfDay())
                .getResultList();
    }
}
