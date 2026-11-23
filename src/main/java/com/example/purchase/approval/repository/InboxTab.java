package com.example.purchase.approval.repository;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 결재함 탭 (APV-01) */
@Getter
@RequiredArgsConstructor
public enum InboxTab {
    PENDING("결재 대기"),
    DONE("내가 처리한 문서"),
    PROGRESS("진행 중 문서");

    private final String label;
}
