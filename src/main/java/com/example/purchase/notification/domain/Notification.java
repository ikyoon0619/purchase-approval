package com.example.purchase.notification.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import com.example.purchase.org.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 사용자에게 보내는 알림 (화면 오른쪽 위 "알림 (3)").
 *
 * <p>dedupKey: 같은 일로 알림이 두 번 생기지 않게 하는 열쇠. 예: 결재 지연은 "APPROVAL_DELAY:{결재선 id}".
 * 배치를 다시 돌려도 같은 결재 단계에는 한 번만 알린다. UNIQUE라서 앱 검사를 뚫고 들어와도 DB가 막는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_notification_member_read", columnList = "member_id, read_at"))
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(length = 200)
    private String linkUrl;

    @Column(unique = true, length = 100)
    private String dedupKey;

    private LocalDateTime readAt;

    public Notification(Member member, String message, String linkUrl, String dedupKey) {
        this.member = member;
        this.message = message;
        this.linkUrl = linkUrl;
        this.dedupKey = dedupKey;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public void markRead(LocalDateTime now) {
        if (readAt == null) {
            readAt = now;
        }
    }
}
