package com.example.purchase.request.domain;

import com.example.purchase.master.domain.Item;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 요청서의 품목 한 줄. 금액 = 수량 × 단가 (BR-07) */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseRequestItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id")
    private PurchaseRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id")
    private Item item;

    @Column(nullable = false)
    private int lineNo;

    @Column(nullable = false)
    private int quantity;

    /** 요청 단가. 기준 단가에서 시작하지만 견적에 따라 바꿀 수 있다 */
    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal amount;

    PurchaseRequestItem(PurchaseRequest request, int lineNo, Item item, int quantity, BigDecimal unitPrice) {
        if (quantity < 1) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }
        this.request = request;
        this.lineNo = lineNo;
        this.item = item;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.amount = unitPrice.multiply(BigDecimal.valueOf(quantity)); // 서버에서 계산
    }
}
