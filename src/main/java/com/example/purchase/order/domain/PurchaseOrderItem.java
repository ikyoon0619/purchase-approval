package com.example.purchase.order.domain;

import com.example.purchase.common.entity.BaseTimeEntity;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Item;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 발주 품목 한 줄. 발주 수량과 실제 입고 수량을 따로 둔다 (덜 들어온 경우를 기록) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private PurchaseOrder order;

    @Column(nullable = false)
    private int lineNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal unitPrice;

    /** 입고 전에는 null */
    private Integer receivedQuantity;

    PurchaseOrderItem(PurchaseOrder order, int lineNo, Item item, int quantity, BigDecimal unitPrice) {
        this.order = order;
        this.lineNo = lineNo;
        this.item = item;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    void receive(int received) {
        if (received < 0 || received > quantity) {
            throw new BusinessException(ErrorCode.INVALID_RECEIVE_QUANTITY,
                    lineNo + "번 줄 입고 수량은 0 이상 " + quantity + " 이하로 입력해 주세요.");
        }
        this.receivedQuantity = received;
    }
}
