package com.example.purchase.request.repository;

import com.example.purchase.request.domain.PurchaseRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface PurchaseRequestRepositoryCustom {

    /** 기안자 본인의 요청서 목록. 거래처(N:1)는 fetch join, 품목 줄(1:N)은 batch fetch */
    Page<PurchaseRequest> searchMine(Long requesterId, RequestSearchCond cond, Pageable pageable);

    List<PurchaseRequest> searchMineAll(Long requesterId, RequestSearchCond cond);
}
