package com.example.purchase.errorlog.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 처리하지 못한 예외 (OPS-02). 사용자에게는 "오류 번호"만 보여 주고, 관리자는 이 번호로 자세한 내용을 찾는다 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_error_log_created", columnList = "created_at"))
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 화면 요청이면 "GET /requests/3", 배치면 "BATCH nightlyJob" */
    @Column(nullable = false, length = 300)
    private String source;

    @Column(length = 30)
    private String loginId;

    @Column(nullable = false, length = 200)
    private String exceptionClass;

    @Column(length = 1000)
    private String message;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String stackTrace;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ErrorLog(String source, String loginId, Throwable e, String stackTrace, LocalDateTime createdAt) {
        this.source = cut(source, 300);
        this.loginId = loginId;
        this.exceptionClass = cut(e.getClass().getName(), 200);
        this.message = cut(e.getMessage(), 1000);
        this.stackTrace = cut(stackTrace, 20000);
        this.createdAt = createdAt;
    }

    private static String cut(String s, int max) {
        return s == null ? null : s.substring(0, Math.min(s.length(), max));
    }
}
