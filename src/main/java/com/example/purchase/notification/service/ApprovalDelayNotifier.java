package com.example.purchase.notification.service;

import com.example.purchase.approval.repository.ApprovalLineRepository;
import com.example.purchase.notification.domain.Notification;
import com.example.purchase.notification.repository.NotificationRepository;
import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.PurchaseRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 결재 지연 알림 (APV-05). 매일 밤 배치의 두 번째 step이 부른다.
 * 결재 차례가 된 지 N일(기본 3일)이 넘은 단계의 결재자에게 알림을 하나 만든다.
 * 같은 결재 단계에는 한 번만 알린다 (dedupKey). 그래서 배치를 몇 번 다시 돌려도 알림이 쌓이지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ApprovalDelayNotifier {

    static final String KEY_PREFIX = "APPROVAL_DELAY:";

    private final ApprovalLineRepository approvalLineRepository;
    private final NotificationRepository notificationRepository;

    @Value("${app.batch.approval-delay-days:3}")
    private int delayDays;

    /** @return 새로 만든 알림 수 */
    @Transactional
    public int notifyDelayed(LocalDateTime now) {
        List<ApprovalLine> lines = approvalLineRepository.findPendingSince(now.minusDays(delayDays));
        if (lines.isEmpty()) {
            return 0;
        }
        Set<String> sent = new HashSet<>(notificationRepository.findExistingKeys(
                lines.stream().map(l -> KEY_PREFIX + l.getId()).toList()));
        int created = 0;
        for (ApprovalLine line : lines) {
            String key = KEY_PREFIX + line.getId();
            if (sent.contains(key)) {
                continue;
            }
            PurchaseRequest r = line.getRequest();
            LocalDateTime since = line.getPendingAt() != null ? line.getPendingAt() : line.getCreatedAt();
            long days = Duration.between(since, now).toDays();
            String message = "[%s] %s: %s 결재를 %d일째 기다리고 있습니다."
                    .formatted(r.getRequestNo(), r.getTitle(), line.getStepName(), days);
            notificationRepository.save(new Notification(line.getApprover(), message, "/requests/" + r.getId(), key));
            created++;
        }
        return created;
    }
}
