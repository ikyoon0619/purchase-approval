package com.example.purchase.request.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import com.example.purchase.org.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 결재선 한 단계 (팀장, 본부장, 구매팀장).
 * 상신할 때 조직도에서 결재자를 찾아 "행으로 저장"한다. 그래야 나중에 조직도가 바뀌어도
 * 이미 올라간 문서의 결재자는 바뀌지 않는다 (docs/02-ERD.md).
 *
 * 상태는 요청서(애그리거트 루트)의 메서드를 통해서만 바뀐다. 그래서 변경 메서드는 package-private.
 * round: 회수했다가 다시 상신하면 1, 2, … 로 늘어난다. 이전 회차 행은 CANCELED로 남겨 이력으로 쓴다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_approval_line_approver_status", columnList = "approver_id, status"))
public class ApprovalLine extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private PurchaseRequest request;

    @Column(nullable = false)
    private int round;

    @Column(nullable = false)
    private int stepOrder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_id")
    private Member approver;

    @Column(nullable = false, length = 20)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineStatus status;

    @Column(length = 500)
    private String comment;

    private LocalDateTime processedAt;

    /**
     * 이 단계가 결재 차례(PENDING)가 된 시각. 결재 지연 알림(APV-05)이 "며칠째 기다리는지"를 여기서 센다.
     * 9단계에서 추가해서 그 전에 만든 행은 비어 있다 → 조회할 때 created_at으로 대신한다.
     */
    private LocalDateTime pendingAt;

    ApprovalLine(PurchaseRequest request, int round, int stepOrder, Member approver, String stepName, LineStatus status,
                 LocalDateTime now) {
        this.request = request;
        this.round = round;
        this.stepOrder = stepOrder;
        this.approver = approver;
        this.stepName = stepName;
        this.status = status;
        if (status == LineStatus.PENDING) {
            this.pendingAt = now;
        }
    }

    /** 프록시의 getId()는 DB를 읽지 않는다 */
    public boolean isApprover(Long memberId) {
        return approver.getId().equals(memberId);
    }

    void makePending(LocalDateTime now) {
        this.status = LineStatus.PENDING;
        this.pendingAt = now;
    }

    void approve(String comment, LocalDateTime now) {
        this.status = LineStatus.APPROVED;
        this.comment = comment;
        this.processedAt = now;
    }

    void reject(String comment, LocalDateTime now) {
        this.status = LineStatus.REJECTED;
        this.comment = comment;
        this.processedAt = now;
    }

    void cancel() {
        this.status = LineStatus.CANCELED;
    }
}
