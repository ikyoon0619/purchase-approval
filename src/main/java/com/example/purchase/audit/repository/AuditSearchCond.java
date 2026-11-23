package com.example.purchase.audit.repository;

import com.example.purchase.audit.AuditAction;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 감사 로그 검색 조건. 비어 있는 값은 조건에서 빠진다 */
public record AuditSearchCond(String loginId, AuditAction action, String targetType, String targetId, Boolean success,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    public static AuditSearchCond empty() {
        return new AuditSearchCond(null, null, null, null, null, null, null);
    }
}
