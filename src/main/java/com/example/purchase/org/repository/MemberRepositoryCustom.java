package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MemberRepositoryCustom {

    /**
     * 사용자 목록 한 페이지. 부서·직급(@ManyToOne)을 fetch join으로 함께 읽는다.
     * ManyToOne fetch join은 행 수가 늘지 않아서 페이징과 같이 써도 안전하다.
     */
    Page<Member> search(MemberSearchCond cond, Pageable pageable);

    /** 엑셀 다운로드용: 같은 조건으로 전부 */
    List<Member> searchAll(MemberSearchCond cond);
}
