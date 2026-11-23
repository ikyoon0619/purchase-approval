package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Role;

/** 사용자 목록 검색 조건. 비어 있는 값은 조건에서 빠진다 */
public record MemberSearchCond(String keyword, Long departmentId, Role role) {

    public static MemberSearchCond empty() {
        return new MemberSearchCond(null, null, null);
    }
}
