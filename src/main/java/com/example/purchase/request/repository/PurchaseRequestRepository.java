package com.example.purchase.request.repository;

import com.example.purchase.request.domain.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, Long>, PurchaseRequestRepositoryCustom {

    /**
     * 상세 화면용. N:1(기안자, 부서, 거래처)은 fetch join으로 한 번에.
     * 1:N(품목 줄, 첨부)은 여기서 fetch join 하지 않는다:
     * List 컬렉션 두 개를 동시에 fetch join하면 MultipleBagFetchException이 난다. → batch fetch로 읽는다.
     */
    @Query("""
            select r from PurchaseRequest r
            join fetch r.requester
            join fetch r.department
            join fetch r.vendor
            where r.id = :id
            """)
    Optional<PurchaseRequest> findDetail(@Param("id") Long id);
}
