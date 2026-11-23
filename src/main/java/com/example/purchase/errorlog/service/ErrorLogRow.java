package com.example.purchase.errorlog.service;

import java.time.LocalDateTime;

public record ErrorLogRow(Long id, String source, String loginId, String exceptionClass, String message,
                          LocalDateTime createdAt) {

    /** 목록에는 클래스 이름만 (패키지 빼고) */
    public String shortClass() {
        int dot = exceptionClass.lastIndexOf('.');
        return dot < 0 ? exceptionClass : exceptionClass.substring(dot + 1);
    }
}
