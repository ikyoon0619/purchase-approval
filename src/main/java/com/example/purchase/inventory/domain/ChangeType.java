package com.example.purchase.inventory.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChangeType {
    IN("입고", "ok"),
    OUT("출고", "warn"),
    ADJUST("조정", "muted");

    private final String label;
    private final String css;
}
