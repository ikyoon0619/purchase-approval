package com.example.purchase.order;

import com.example.purchase.inventory.domain.Inventory;
import com.example.purchase.inventory.repository.InventoryHistoryRepository;
import com.example.purchase.inventory.repository.InventoryRepository;
import com.example.purchase.inventory.service.InventoryService;
import com.example.purchase.master.domain.Item;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 품목을 동시에 입고할 때 재고 수량이 어긋나는 문제 (BR-08, docs/troubleshooting/05).
 * 실제 커밋이 필요하므로 @Transactional을 붙이지 않고, 끝나면 테이블을 직접 비운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Autowired InventoryService inventoryService;
    @Autowired InventoryRepository inventoryRepository;
    @Autowired InventoryHistoryRepository historyRepository;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager txManager;
    @Autowired JdbcTemplate jdbc;

    TransactionTemplate tx;
    TransactionTemplate newTx;
    Item item;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(txManager);
        newTx = new TransactionTemplate(txManager);
        newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(s -> {
            item = new Item("IT-0001", "노트북", "IT", "EA", new BigDecimal("1650000"));
            em.persist(item);
            em.persist(new Inventory(item));
        });
    }

    @AfterEach
    void cleanUp() {
        for (String t : new String[]{"inventory_history", "inventory", "item"}) {
            jdbc.update("delete from " + t);
        }
    }

    @Test
    @DisplayName("[문제] 락 없이 '읽고 → 더하고 → 저장'하면 먼저 커밋한 입고 5개가 사라진다 (Lost Update)")
    void lostUpdateWithoutLock() {
        tx.executeWithoutResult(s -> {
            Inventory a = inventoryRepository.findByItemId(item.getId()).orElseThrow();   // A: 0개로 읽음

            newTx.executeWithoutResult(s2 -> {                                            // 그사이 B: 0 → 5, 커밋
                inventoryRepository.findByItemId(item.getId()).orElseThrow().increase(5);
            });
            assertThat(quantity()).isEqualTo(5);

            a.increase(3);                                                                // A: 0 + 3 = 3 으로 덮어씀
        });

        assertThat(quantity()).isEqualTo(3); // 기대값 8. B의 입고 5개가 사라졌다
    }

    @Test
    @DisplayName("[해결] SELECT … FOR UPDATE로 잠그면 10명이 동시에 1개씩 입고해도 정확히 10개, 이력도 1~10 순서대로")
    void concurrentReceiveWithPessimisticLock() throws Exception {
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                ready.countDown();
                start.await();                       // 10개 스레드가 모두 준비되면 한꺼번에 출발
                return inventoryService.receive(item, 1, null, "동시 입고");
            }));
        }
        ready.await();
        start.countDown();
        List<Integer> afterQuantities = new ArrayList<>();
        for (Future<Integer> r : results) {
            afterQuantities.add(r.get(20, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(quantity()).isEqualTo(10);
        // 각 트랜잭션이 본 "처리 후 수량"이 1~10으로 겹치지 않는다 = 한 줄로 서서 차례대로 처리됐다
        assertThat(afterQuantities).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(historyRepository.countByItemId(item.getId())).isEqualTo(10);
    }

    private int quantity() {
        return jdbc.queryForObject("select quantity from inventory where item_id = ?", Integer.class, item.getId());
    }
}
