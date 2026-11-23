package com.example.purchase.inventory.domain;

import com.example.purchase.common.entity.BaseEntity;
import com.example.purchase.master.domain.Item;
import com.example.purchase.order.domain.PurchaseOrder;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 재고 이력 (INV-02). 누가(createdBy) 언제(createdAt) 왜(changeType, memo, order) 늘고 줄었는지.
 * afterQty: 처리 직후 수량. 락 안에서 기록하므로 이력을 순서대로 읽으면 수량 흐름이 그대로 맞아떨어진다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = @Index(name = "idx_inventory_history_item", columnList = "item_id, id"))
public class InventoryHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Enumerated(EnumType.STRING)
    // MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다 (troubleshooting 09)
    @Column(nullable = false, length = 10, columnDefinition = "varchar(10)")
    private ChangeType changeType;

    @Column(nullable = false)
    private int changeQty;

    @Column(nullable = false)
    private int afterQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    @Column(length = 200)
    private String memo;

    public InventoryHistory(Item item, ChangeType changeType, int changeQty, int afterQty, PurchaseOrder order, String memo) {
        this.item = item;
        this.changeType = changeType;
        this.changeQty = changeQty;
        this.afterQty = afterQty;
        this.order = order;
        this.memo = memo;
    }
}
