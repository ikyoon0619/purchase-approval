package com.example.purchase.inventory.service;

import com.example.purchase.inventory.domain.ChangeType;
import com.example.purchase.inventory.domain.InventoryHistory;

import java.time.LocalDateTime;

/** 재고 이력 한 줄 */
public record HistoryRow(Long id, ChangeType changeType, int changeQty, int afterQty, Long orderId, String orderNo,
                         String memo, String createdBy, LocalDateTime createdAt) {

    static HistoryRow from(InventoryHistory h) {
        return new HistoryRow(h.getId(), h.getChangeType(), h.getChangeQty(), h.getAfterQty(),
                h.getOrder() == null ? null : h.getOrder().getId(),
                h.getOrder() == null ? null : h.getOrder().getOrderNo(),
                h.getMemo(), h.getCreatedBy(), h.getCreatedAt());
    }
}
