package com.example.purchase.order.service;

import com.example.purchase.request.domain.PurchaseRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 발주 대기 (최종 승인된 요청서) 한 줄 */
public record WaitingRow(Long requestId, String requestNo, String title, String requesterName, String departmentName,
                         String vendorName, BigDecimal totalAmount, LocalDateTime approvedAt) {

    static WaitingRow from(PurchaseRequest r) {
        return new WaitingRow(r.getId(), r.getRequestNo(), r.getTitle(), r.getRequester().getName(),
                r.getDepartment().getName(), r.getVendor().getName(), r.getTotalAmount(), r.getCompletedAt());
    }
}
