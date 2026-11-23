package com.example.purchase.request.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 결재선 한 단계의 상태 (docs/04-결재-상태도.md 2장) */
@Getter
@RequiredArgsConstructor
public enum LineStatus {
    WAITING("대기", "muted"),
    PENDING("결재 차례", "warn"),
    APPROVED("승인", "ok"),
    REJECTED("반려", "off"),
    CANCELED("취소", "muted");

    private final String label;
    private final String css;
}
