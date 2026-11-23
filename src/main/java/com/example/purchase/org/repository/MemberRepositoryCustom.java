package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;

import java.util.List;

public interface MemberRepositoryCustom {

    /** 사용자 목록. 부서·직급을 fetch join으로 함께 읽어 쿼리 한 번으로 끝낸다 */
    List<Member> search(MemberSearchCond cond);
}
