package com.example.purchase.order.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.inventory.service.InventoryService;
import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.domain.PurchaseOrder;
import com.example.purchase.order.domain.PurchaseOrderItem;
import com.example.purchase.order.repository.PurchaseOrderRepository;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 발주 등록과 입고 처리 (ORD-01, ORD-02) */
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final PurchaseOrderRepository orderRepository;
    private final PurchaseRequestRepository requestRepository;
    private final InventoryService inventoryService;

    /** 최종 승인된 요청서로 발주서를 만든다. 요청서는 ORDERED로 */
    @Audited(action = AuditAction.ORDER, target = "발주서", id = "#result")
    public Long create(Long requestId) {
        PurchaseRequest request = requestRepository.findDetail(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        if (orderRepository.existsByRequestId(requestId)) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        LocalDateTime now = LocalDateTime.now();
        PurchaseOrder order = orderRepository.save(PurchaseOrder.create(request, now));
        order.assignOrderNo(now);
        return order.getId();
    }

    /**
     * 입고 처리. 발주서에 입고 수량을 적고, 품목마다 재고 행을 잠근 뒤 늘린다.
     * 잠그는 순서를 품목 id 순으로 고정한다: 발주 A가 (노트북→모니터), 발주 B가 (모니터→노트북) 순서로 잠그면
     * 서로 상대가 잡은 행을 기다리는 데드락이 생길 수 있다.
     */
    @Audited(action = AuditAction.RECEIVE, target = "발주서", id = "#orderId")
    public void receive(Long orderId, Map<Long, Integer> receivedByLineId) {
        PurchaseOrder order = findDetail(orderId);
        LocalDateTime now = LocalDateTime.now();
        order.receive(receivedByLineId, now); // 수량 검증 + 상태 변경 (먼저 해서 잘못된 입력이면 재고를 건드리지 않음)

        List<PurchaseOrderItem> lines = order.getItems().stream()
                .filter(l -> l.getReceivedQuantity() > 0)
                .sorted(Comparator.comparing(l -> l.getItem().getId()))
                .toList();
        for (PurchaseOrderItem line : lines) {
            inventoryService.receive(line.getItem(), line.getReceivedQuantity(), order, "발주 입고 " + order.getOrderNo());
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderRow> search(OrderStatus status, Pageable pageable) {
        return orderRepository.search(status, pageable).map(OrderRow::from);
    }

    @Transactional(readOnly = true)
    public Page<WaitingRow> waiting(Pageable pageable) {
        return requestRepository.findApprovedForOrder(pageable).map(WaitingRow::from);
    }

    @Transactional(readOnly = true)
    public OrderDetail getDetail(Long id) {
        return OrderDetail.from(findDetail(id));
    }

    private PurchaseOrder findDetail(Long id) {
        return orderRepository.findDetail(id).orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    }
}
