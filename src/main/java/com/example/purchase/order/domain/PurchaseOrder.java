package com.example.purchase.order.domain;

import com.example.purchase.common.entity.BaseEntity;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.PurchaseRequestItem;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 발주서 (ORD-01). 최종 승인된 요청서 하나로 발주서 하나를 만든다 (request_id UNIQUE).
 * 품목 줄은 요청서에서 복사해 둔다: 입고 수량을 줄마다 따로 적어야 하고, 요청서는 결재가 끝난 문서라 건드리지 않는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, length = 20)
    private String orderNo;

    /** 같은 요청서로 발주를 두 번 만들지 못하게 DB에서도 막는다 (unique) */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", unique = true)
    private PurchaseRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Enumerated(EnumType.STRING)
    // MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다 (troubleshooting 09)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private OrderStatus status = OrderStatus.ORDERED;

    @Column(nullable = false)
    private LocalDateTime orderedAt;

    private LocalDateTime receivedAt;

    /** 같은 발주서를 두 사람이 동시에 입고 처리하면 나중 커밋이 실패한다 (재고도 같이 롤백) */
    @Version
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo asc")
    private List<PurchaseOrderItem> items = new ArrayList<>();

    private PurchaseOrder(PurchaseRequest request, LocalDateTime now) {
        this.request = request;
        this.vendor = request.getVendor();
        this.orderedAt = now;
        for (PurchaseRequestItem i : request.getItems()) {
            items.add(new PurchaseOrderItem(this, i.getLineNo(), i.getItem(), i.getQuantity(), i.getUnitPrice()));
        }
    }

    /** 요청서 상태도 같이 바꾼다 (APPROVED → ORDERED). 승인 안 된 문서면 요청서가 예외를 던진다 */
    public static PurchaseOrder create(PurchaseRequest request, LocalDateTime now) {
        request.markOrdered();
        return new PurchaseOrder(request, now);
    }

    /** 저장해서 id가 생긴 뒤 호출. PO-202611-0001 */
    public void assignOrderNo(LocalDateTime now) {
        this.orderNo = "PO-" + now.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + String.format("%04d", id);
    }

    /**
     * 입고 수량 기록 (ORD-02). 재고 반영은 서비스가 재고 행을 잠근 뒤에 한다.
     * @param receivedByLineId 발주 줄 id → 입고 수량 (0 ~ 발주 수량)
     */
    public void receive(Map<Long, Integer> receivedByLineId, LocalDateTime now) {
        if (status != OrderStatus.ORDERED) {
            throw new BusinessException(ErrorCode.ALREADY_RECEIVED);
        }
        for (PurchaseOrderItem line : items) {
            line.receive(receivedByLineId.getOrDefault(line.getId(), line.getQuantity()));
        }
        status = OrderStatus.RECEIVED;
        receivedAt = now;
        request.markReceived();
    }
}
