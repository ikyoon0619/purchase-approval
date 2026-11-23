package com.example.purchase.notification.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.notification.domain.Notification;
import com.example.purchase.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public long unreadCount(Long memberId) {
        return notificationRepository.countByMemberIdAndReadAtIsNull(memberId);
    }

    public Page<NotificationRow> list(Long memberId, Pageable pageable) {
        return notificationRepository.findByMemberIdOrderByIdDesc(memberId, pageable).map(NotificationRow::of);
    }

    public List<NotificationRow> recent(Long memberId, int size) {
        return list(memberId, PageRequest.of(0, size)).getContent();
    }

    /** 알림을 누르면 읽음으로 바꾸고 연결된 화면 주소를 돌려준다. 남의 알림은 못 연다 */
    @Transactional
    public String open(Long id, Long memberId) {
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getMember().getId().equals(memberId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
        n.markRead(LocalDateTime.now());
        return n.getLinkUrl() == null ? "/notifications" : n.getLinkUrl();
    }

    @Transactional
    public int readAll(Long memberId) {
        return notificationRepository.markAllRead(memberId, LocalDateTime.now());
    }
}
