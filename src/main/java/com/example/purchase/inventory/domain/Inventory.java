package com.example.purchase.inventory.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Item;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 품목별 현재 재고 (품목당 한 줄, item_id UNIQUE).
 * 수량을 바꿀 때는 반드시 InventoryRepository.findByItemIdForUpdate(비관적 락)로 읽은 뒤에 바꾼다.
 * 락 없이 "읽고 → 더하고 → 저장"하면 동시에 들어온 입고 하나가 사라진다 (docs/troubleshooting/05).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", unique = true)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    public Inventory(Item item) {
        this.item = item;
    }

    public void increase(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("증가 수량은 0 이상");
        }
        quantity += amount;
    }

    /** 재고는 음수가 될 수 없다 (BR-08) */
    public void decrease(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출고 수량은 1 이상이어야 합니다.");
        }
        if (quantity < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                    "재고가 부족합니다. 현재 " + quantity + ", 요청 " + amount);
        }
        quantity -= amount;
    }
}
