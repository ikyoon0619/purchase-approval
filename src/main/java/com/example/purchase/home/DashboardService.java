package com.example.purchase.home;

import com.example.purchase.notification.service.NotificationRow;
import com.example.purchase.notification.service.NotificationService;
import com.example.purchase.request.domain.RequestStatus;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.security.LoginUser;
import com.example.purchase.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/** 첫 화면(대시보드, STA-02)에 필요한 숫자를 트랜잭션 하나에서 모아 DTO로 넘긴다 (OSIV가 꺼져 있으므로) */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final PurchaseRequestRepository requestRepository;
    private final StatsService statsService;
    private final NotificationService notificationService;

    public Dashboard load(LoginUser user) {
        YearMonth now = YearMonth.now();
        List<StatsService.Bar> byDept = statsService.byDepartment(now);
        BigDecimal myDeptThisMonth = byDept.stream().filter(b -> b.label().equals(user.getDepartmentName()))
                .map(StatsService.Bar::amount).findFirst().orElse(BigDecimal.ZERO);
        return new Dashboard(
                requestRepository.countByRequesterIdAndStatus(user.getId(), RequestStatus.DRAFT),
                requestRepository.countByRequesterIdAndStatus(user.getId(), RequestStatus.IN_APPROVAL),
                myDeptThisMonth,
                statsService.departmentTrend(user.getDepartmentId(), now, 6),
                byDept,
                notificationService.recent(user.getId(), 5),
                statsService.lastAggregatedAt().orElse(null));
    }

    public record Dashboard(long myDrafts, long myInApproval, BigDecimal myDeptThisMonth,
                            List<StatsService.Bar> trend, List<StatsService.Bar> byDepartment,
                            List<NotificationRow> notifications, LocalDateTime lastAggregatedAt) {
    }
}
