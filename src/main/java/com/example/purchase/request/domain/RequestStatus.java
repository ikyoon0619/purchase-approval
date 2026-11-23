package com.example.purchase.request.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 구매요청서 상태 (docs/04-결재-상태도.md). 6단계부터 DRAFT 외의 상태로 바뀐다 */
@Getter
@RequiredArgsConstructor
public enum RequestStatus {
    DRAFT("임시저장", "muted"),
    IN_APPROVAL("결재중", "warn"),
    APPROVED("승인", "ok"),
    REJECTED("반려", "off"),
    ORDERED("발주", "ok"),
    RECEIVED("입고완료", "ok");

    private final String label;
    private final String css;
}
