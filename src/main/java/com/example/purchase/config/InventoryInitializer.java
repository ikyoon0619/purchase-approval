package com.example.purchase.config;

import com.example.purchase.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 재고 행이 없는 품목에 재고 0개 행을 만든다. 모든 환경에서 돈다.
 * 7단계 이전에 만든 품목(로컬 DB에 이미 있는 품목)도 입고할 수 있게 하려는 것. 샘플 데이터 다음에 돈다.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class InventoryInitializer implements ApplicationRunner {

    private final InventoryService inventoryService;

    @Override
    public void run(ApplicationArguments args) {
        int filled = inventoryService.fillMissing();
        if (filled > 0) {
            log.info("재고 행이 없던 품목 {}개에 재고 0을 만들었습니다.", filled);
        }
    }
}
