package com.example.purchase.audit.domain;

import com.example.purchase.audit.AuditAction;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 누가 언제 무엇을 했는지 (OPS-01). 지우거나 고치지 않는 기록이라 setter가 없다.
 *
 * <p>member는 FK가 아니라 id와 로그인 아이디를 값으로 복사해 둔다. 기록은 사용자 정보가 바뀌어도
 * "그때 누가"를 그대로 보여 줘야 하고, 기록 저장이 회원 테이블 상태 때문에 실패하면 안 되기 때문이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_audit_log_created", columnList = "created_at"),
        @Index(name = "idx_audit_log_login", columnList = "login_id, created_at")})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long memberId;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    @Enumerated(EnumType.STRING)
    // MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다 (troubleshooting 09)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private AuditAction action;

    @Column(nullable = false, length = 30)
    private String targetType;

    @Column(length = 50)
    private String targetId;

    /** 성공했나. 실패(예외, 롤백)도 남긴다: "누가 남의 문서를 지우려 했다"도 감사 대상 */
    @Column(nullable = false)
    private boolean success;

    @Column(length = 500)
    private String detail;

    @Column(length = 45)
    private String ip;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLog(Long memberId, String loginId, AuditAction action, String targetType, String targetId,
                    boolean success, String detail, String ip, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.loginId = loginId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.success = success;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 500));
        this.ip = ip;
        this.createdAt = createdAt;
    }
}
