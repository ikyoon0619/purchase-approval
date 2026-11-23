package com.example.purchase.master.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 거래처 사업자 상태. 8단계에서 국세청 API로 자동 확인한다 */
@Getter
@RequiredArgsConstructor
public enum VendorStatus {
    ACTIVE("정상", "ok"),
    SUSPENDED("휴업", "warn"),
    CLOSED("폐업", "off"),
    UNKNOWN("확인 안 됨", "muted");

    private final String label;
    private final String css;

    /** 새 요청서에서 고를 수 있는 상태인가 (BR-09: 휴업·폐업은 불가, 확인 안 됨은 허용) */
    public boolean selectable() {
        return this == ACTIVE || this == UNKNOWN;
    }
}
