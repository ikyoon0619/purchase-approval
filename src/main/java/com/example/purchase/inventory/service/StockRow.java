package com.example.purchase.inventory.service;

import com.example.purchase.inventory.domain.Inventory;
import com.example.purchase.master.domain.Item;

import java.time.LocalDateTime;

/** 재고 현황 한 줄 */
public record StockRow(Long itemId, String itemCode, String itemName, String categoryCode, String unitCode,
                       int quantity, LocalDateTime updatedAt) {

    static StockRow from(Inventory v) {
        Item i = v.getItem();
        return new StockRow(i.getId(), i.getCode(), i.getName(), i.getCategoryCode(), i.getUnitCode(),
                v.getQuantity(), v.getUpdatedAt());
    }
}
