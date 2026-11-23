package com.example.purchase.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * @Transactional 없이 실제로 커밋하는 테스트(배치, 동시성)의 뒷정리. 자식 테이블부터 지운다.
 * 기본 메뉴·공통코드와 Spring Batch 실행 이력(BATCH_ 테이블)은 남긴다.
 */
public final class DbCleaner {

    private static final String[] TABLES = {
            "notification", "monthly_purchase_stat",
            "inventory_history", "inventory", "purchase_order_item", "purchase_order",
            "approval_line", "attachment", "purchase_request_item", "purchase_request",
            "item", "vendor", "member", "position"};

    private DbCleaner() {
    }

    public static void clean(JdbcTemplate jdbc) {
        for (String table : TABLES) {
            jdbc.update("delete from " + table);
        }
        jdbc.update("delete from department where parent_id is not null");
        jdbc.update("delete from department");
    }
}
