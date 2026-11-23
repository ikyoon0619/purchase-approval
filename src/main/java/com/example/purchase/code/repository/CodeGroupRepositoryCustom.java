package com.example.purchase.code.repository;

import com.example.purchase.code.domain.CodeGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CodeGroupRepositoryCustom {

    /**
     * 그룹 목록 페이지. 코드(컬렉션)는 fetch join 하지 않는다.
     * 컬렉션 fetch join + 페이징은 DB에서 자르지 못해 전체를 메모리에 올리기 때문 (docs/troubleshooting/02).
     */
    Page<CodeGroup> search(String keyword, Pageable pageable);
}
