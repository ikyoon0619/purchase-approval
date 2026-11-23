package com.example.purchase.notification.repository;

import com.example.purchase.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByMemberIdAndReadAtIsNull(Long memberId);

    Page<Notification> findByMemberIdOrderByIdDesc(Long memberId, Pageable pageable);

    /** 이미 보낸 알림 열쇠를 한 번에 확인 (대상 100건이면 exists 쿼리 100번 대신 1번) */
    @Query("select n.dedupKey from Notification n where n.dedupKey in :keys")
    List<String> findExistingKeys(@Param("keys") Collection<String> keys);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.member.id = :memberId and n.readAt is null")
    int markAllRead(@Param("memberId") Long memberId, @Param("now") LocalDateTime now);
}
