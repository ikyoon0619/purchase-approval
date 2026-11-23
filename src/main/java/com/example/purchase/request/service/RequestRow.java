package com.example.purchase.request.service;

import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.PurchaseRequestItem;
import com.example.purchase.request.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 내 요청 목록 한 줄. 트랜잭션 안에서 만든다 (open-in-view: false) */
public record RequestRow(Long id, String requestNo, String title, String vendorName, String itemSummary,
                         BigDecimal totalAmount, RequestStatus status, LocalDateTime createdAt) {

    public static RequestRow from(PurchaseRequest r) {
        return new RequestRow(r.getId(), r.getRequestNo(), r.getTitle(), r.getVendor().getName(),
                summary(r.getItems()), r.getTotalAmount(), r.getStatus(), r.getCreatedAt());
    }

    /** "노트북 외 2건". 품목 줄과 품목은 batch fetch로 페이지 단위로 한 번에 읽힌다 */
    private static String summary(List<PurchaseRequestItem> items) {
        if (items.isEmpty()) {
            return "-";
        }
        String first = items.get(0).getItem().getName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }
}
