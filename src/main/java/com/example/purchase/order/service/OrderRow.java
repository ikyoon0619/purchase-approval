package com.example.purchase.order.service;

import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.domain.PurchaseOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderRow(Long id, String orderNo, Long requestId, String requestNo, String title, String requesterName,
                       String vendorName, BigDecimal totalAmount, OrderStatus status,
                       LocalDateTime orderedAt, LocalDateTime receivedAt) {

    static OrderRow from(PurchaseOrder o) {
        var r = o.getRequest();
        return new OrderRow(o.getId(), o.getOrderNo(), r.getId(), r.getRequestNo(), r.getTitle(), r.getRequester().getName(),
                o.getVendor().getName(), r.getTotalAmount(), o.getStatus(), o.getOrderedAt(), o.getReceivedAt());
    }
}
