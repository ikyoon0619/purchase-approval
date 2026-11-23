package com.example.purchase.stats.repository;

import com.example.purchase.stats.domain.MonthlyPurchaseStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MonthlyPurchaseStatRepository extends JpaRepository<MonthlyPurchaseStat, Long> {

    Optional<MonthlyPurchaseStat> findByStatMonthAndDepartmentId(String statMonth, Long departmentId);

    /** 통계 화면·대시보드: 기간 안의 모든 부서 통계 + 부서 이름 (fetch join으로 N+1 방지) */
    @Query("""
            select s from MonthlyPurchaseStat s join fetch s.department d
             where s.statMonth between :from and :to
             order by d.code, s.statMonth
            """)
    List<MonthlyPurchaseStat> findBetween(@Param("from") String from, @Param("to") String to);

    @Query("select max(s.aggregatedAt) from MonthlyPurchaseStat s")
    Optional<LocalDateTime> lastAggregatedAt();
}
