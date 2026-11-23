package com.example.purchase;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseApprovalApplicationTests {

    @Test
    void contextLoads() {
        // 설정(빈, JPA 매핑, QueryDSL)이 잘못되면 여기서 앱이 뜨지 않아 실패한다
    }
}
