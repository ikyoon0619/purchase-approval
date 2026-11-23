package com.example.purchase.order.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum OrderStatus {
    ORDERED("발주", "warn"),
    RECEIVED("입고완료", "ok");

    private final String label;
    private final String css;
}
