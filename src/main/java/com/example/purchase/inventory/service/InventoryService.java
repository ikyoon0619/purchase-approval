package com.example.purchase.inventory.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.inventory.domain.ChangeType;
import com.example.purchase.inventory.domain.Inventory;
import com.example.purchase.inventory.domain.InventoryHistory;
import com.example.purchase.inventory.repository.InventoryHistoryRepository;
import com.example.purchase.inventory.repository.InventoryRepository;
import com.example.purchase.master.domain.Item;
import com.example.purchase.master.repository.ItemRepository;
import com.example.purchase.order.domain.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 재고 증감과 이력 (INV-01, INV-02, BR-08).
 * 수량을 바꾸는 메서드는 전부 "행 잠금 → 바꾸기 → 이력 기록" 순서를 지킨다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository historyRepository;
    private final ItemRepository itemRepository;

    /** 입고. 발주 입고(order 있음)와 수동 입고 모두 여기로 */
    public int receive(Item item, int qty, PurchaseOrder order, String memo) {
        Inventory inventory = lock(item.getId());
        inventory.increase(qty);
        historyRepository.save(new InventoryHistory(item, ChangeType.IN, qty, inventory.getQuantity(), order, memo));
        return inventory.getQuantity();
    }

    /** 출고. 재고보다 많이 빼면 INSUFFICIENT_STOCK (재고는 음수가 될 수 없다) */
    @Audited(action = AuditAction.SHIP, target = "재고", id = "#itemId")
    public int ship(Long itemId, int qty, String memo) {
        Inventory inventory = lock(itemId);
        inventory.decrease(qty);
        historyRepository.save(new InventoryHistory(inventory.getItem(), ChangeType.OUT, -qty, inventory.getQuantity(), null, memo));
        return inventory.getQuantity();
    }

    /** 품목을 새로 만들 때 재고 행(0개)도 같이 만든다. 첫 입고 때 만들면 동시 입고 둘이 둘 다 INSERT 하다 충돌한다 */
    public void createFor(Item item) {
        inventoryRepository.save(new Inventory(item));
    }

    /** 재고 행이 없는 품목을 채운다 (이 단계 전에 만든 품목). 시작할 때 한 번 */
    public int fillMissing() {
        var ids = inventoryRepository.findItemIdsWithoutInventory();
        ids.forEach(id -> inventoryRepository.save(new Inventory(itemRepository.getReferenceById(id))));
        return ids.size();
    }

    @Transactional(readOnly = true)
    public Page<StockRow> search(String keyword, boolean onlyInStock, Pageable pageable) {
        return inventoryRepository.search(StringUtils.hasText(keyword) ? keyword.trim() : null, onlyInStock, pageable)
                .map(StockRow::from);
    }

    @Transactional(readOnly = true)
    public StockRow get(Long itemId) {
        return inventoryRepository.findByItemId(itemId).map(StockRow::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public Page<HistoryRow> history(Long itemId, Pageable pageable) {
        return historyRepository.findByItem(itemId, pageable).map(HistoryRow::from);
    }

    private Inventory lock(Long itemId) {
        return inventoryRepository.findByItemIdForUpdate(itemId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVENTORY_NOT_FOUND));
    }
}
