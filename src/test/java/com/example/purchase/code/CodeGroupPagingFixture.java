package com.example.purchase.code;

import com.example.purchase.code.domain.CodeGroup;
import jakarta.persistence.EntityManager;

/** 코드 그룹 30개 × 코드 5개 = 코드 150개를 넣는다 */
final class CodeGroupPagingFixture {

    static final int GROUPS = 30;
    static final int CODES_PER_GROUP = 5;

    private CodeGroupPagingFixture() {
    }

    static void insert(EntityManager em) {
        for (int g = 1; g <= GROUPS; g++) {
            CodeGroup group = new CodeGroup(String.format("GROUP_%02d", g), "그룹" + g);
            for (int c = 1; c <= CODES_PER_GROUP; c++) {
                group.addCode("C" + c, "코드" + c, c * 10);
            }
            em.persist(group); // cascade = ALL → 코드도 저장
        }
        em.flush();
        em.clear();
    }
}
