package com.example.purchase.stats.repository;

import java.math.BigDecimal;

/** 집계 쿼리 한 줄: 부서별 승인 건수와 금액. 배치의 reader가 읽고 writer가 통계 표에 쓴다 */
public record DeptMonthlyAmount(Long departmentId, Long requestCount, BigDecimal totalAmount) {
}
