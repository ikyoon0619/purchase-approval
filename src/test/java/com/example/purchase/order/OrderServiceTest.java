package com.example.purchase.order;

import com.example.purchase.approval.service.ApprovalLineResolver;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.inventory.domain.ChangeType;
import com.example.purchase.inventory.service.HistoryRow;
import com.example.purchase.inventory.service.InventoryService;
import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.domain.PurchaseOrder;
import com.example.purchase.order.service.OrderDetail;
import com.example.purchase.order.service.OrderService;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.RequestStatus;
import com.example.purchase.support.RequestFixture;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/** 발주(ORD-01) → 입고(ORD-02) → 재고·이력(INV-01, 02), 재고 음수 금지(BR-08) */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired InventoryService inventoryService;
    @Autowired ApprovalLineResolver resolver;
    @Autowired EntityManager em;

    RequestFixture f;

    @BeforeEach
    void setUp() {
        f = new RequestFixture(em);
        inventoryService.fillMissing(); // 픽스처 품목은 em.persist로 만들어서 재고 행이 없다
    }

    @Test
    @DisplayName("최종 승인된 요청서만 발주할 수 있고, 같은 요청서로 두 번 발주할 수 없다")
    void createOrder() {
        PurchaseRequest draft = f.draft(f.dev, "임시저장 문서");
        assertThatThrownBy(() -> orderService.create(draft.getId()))
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_ALLOWED);

        Long requestId = approved().getId();
        Long orderId = orderService.create(requestId);
        flushClear();

        OrderDetail o = orderService.getDetail(orderId);
        assertThat(o.orderNo()).startsWith("PO-").endsWith(String.format("-%04d", orderId));
        assertThat(o.lines()).extracting(OrderDetail.Line::itemName, OrderDetail.Line::quantity)
                .containsExactly(tuple("노트북", 2), tuple("모니터", 2), tuple("복사용지", 1));
        assertThat(em.find(PurchaseRequest.class, requestId).getStatus()).isEqualTo(RequestStatus.ORDERED);

        assertThatThrownBy(() -> orderService.create(requestId))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_ORDERED);
    }

    @Test
    @DisplayName("입고: 적은 수량만큼 재고가 늘고(덜 들어온 줄은 덜), 이력에 처리 후 수량과 발주가 남는다")
    void receive() {
        Long orderId = orderService.create(approved().getId());
        flushClear();
        OrderDetail o = orderService.getDetail(orderId);
        Long laptopLine = o.lines().get(0).id();
        Long paperLine = o.lines().get(2).id();

        orderService.receive(orderId, Map.of(laptopLine, 1, paperLine, 0)); // 노트북 2대 중 1대, 복사용지 안 옴, 모니터는 그대로 2
        flushClear();

        assertThat(inventoryService.get(f.laptop.getId()).quantity()).isEqualTo(1);
        assertThat(inventoryService.get(f.monitor.getId()).quantity()).isEqualTo(2);
        assertThat(inventoryService.get(f.paper.getId()).quantity()).isZero();
        assertThat(inventoryService.history(f.paper.getId(), PageRequest.of(0, 10))).isEmpty(); // 0개는 이력도 없음

        HistoryRow h = inventoryService.history(f.laptop.getId(), PageRequest.of(0, 10)).getContent().get(0);
        assertThat(h.changeType()).isEqualTo(ChangeType.IN);
        assertThat(h.afterQty()).isEqualTo(1);
        assertThat(h.orderNo()).isEqualTo(o.orderNo());

        OrderDetail after = orderService.getDetail(orderId);
        assertThat(after.status()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(em.find(PurchaseOrder.class, orderId).getRequest().getStatus()).isEqualTo(RequestStatus.RECEIVED);

        assertThatThrownBy(() -> orderService.receive(orderId, Map.of()))
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_RECEIVED);
    }

    @Test
    @DisplayName("입고 수량이 발주 수량보다 많거나 음수면 거절하고 재고는 그대로")
    void invalidReceiveQuantity() {
        Long orderId = orderService.create(approved().getId());
        flushClear();
        Long laptopLine = orderService.getDetail(orderId).lines().get(0).id();

        assertThatThrownBy(() -> orderService.receive(orderId, Map.of(laptopLine, 3)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_RECEIVE_QUANTITY);
        assertThat(inventoryService.get(f.laptop.getId()).quantity()).isZero();
    }

    @Test
    @DisplayName("출고: 재고보다 많이 빼면 거절 (재고는 음수가 될 수 없다)")
    void shipMoreThanStock() {
        inventoryService.receive(f.laptop, 3, null, "수동 입고");

        assertThat(inventoryService.ship(f.laptop.getId(), 2, "지급")).isEqualTo(1);
        assertThatThrownBy(() -> inventoryService.ship(f.laptop.getId(), 2, "지급"))
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        assertThat(inventoryService.history(f.laptop.getId(), PageRequest.of(0, 10)).getContent())
                .extracting(HistoryRow::changeQty, HistoryRow::afterQty)
                .containsExactly(tuple(-2, 1), tuple(3, 3)); // 최신순
    }

    /** 노트북 2 + 모니터 2 + 복사용지 1 요청서를 팀장·본부장까지 승인한 상태로 */
    private PurchaseRequest approved() {
        PurchaseRequest r = f.draft(f.dev, "노트북 구매");
        LocalDateTime now = LocalDateTime.now();
        r.submit(resolver.resolve(r), now);
        r.approve(f.devLead.getId(), null, now);
        r.approve(f.bizHead.getId(), null, now);
        em.flush();
        return r;
    }

    private void flushClear() {
        em.flush();
        em.clear();
    }
}
