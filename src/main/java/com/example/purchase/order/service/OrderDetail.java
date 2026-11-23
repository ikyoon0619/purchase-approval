package com.example.purchase.order.service;

import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.domain.PurchaseOrder;
import com.example.purchase.order.domain.PurchaseOrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 발주 상세·입고 화면 (SCR-302) */
public record OrderDetail(Long id, String orderNo, OrderStatus status, Long requestId, String requestNo, String title,
                          String requesterName, String departmentName, String vendorName, String vendorBizNo,
                          BigDecimal totalAmount, LocalDateTime orderedAt, String orderedBy, LocalDateTime receivedAt,
                          List<Line> lines) {

    public record Line(Long id, int lineNo, Long itemId, String itemCode, String itemName, int quantity,
                       BigDecimal unitPrice, Integer receivedQuantity) {
    }

    public boolean receivable() {
        return status == OrderStatus.ORDERED;
    }

    static OrderDetail from(PurchaseOrder o) {
        var r = o.getRequest();
        List<Line> lines = o.getItems().stream().map((PurchaseOrderItem i) -> new Line(i.getId(), i.getLineNo(),
                i.getItem().getId(), i.getItem().getCode(), i.getItem().getName(), i.getQuantity(), i.getUnitPrice(),
                i.getReceivedQuantity())).toList();
        return new OrderDetail(o.getId(), o.getOrderNo(), o.getStatus(), r.getId(), r.getRequestNo(), r.getTitle(),
                r.getRequester().getName(), r.getDepartment().getName(), o.getVendor().getName(),
                o.getVendor().getBizNoFormatted(), r.getTotalAmount(), o.getOrderedAt(), o.getCreatedBy(),
                o.getReceivedAt(), lines);
    }
}
