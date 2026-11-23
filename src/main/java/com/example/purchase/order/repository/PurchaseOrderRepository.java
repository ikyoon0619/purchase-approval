package com.example.purchase.order.repository;

import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.domain.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

    boolean existsByRequestId(Long requestId);

    /** 발주 목록. 요청서·거래처·기안자는 N:1 → fetch join + 페이징 OK */
    @Query(value = """
            select o from PurchaseOrder o
            join fetch o.request r join fetch r.requester join fetch o.vendor
            where (:status is null or o.status = :status)
            order by o.id desc
            """,
            countQuery = "select count(o) from PurchaseOrder o where (:status is null or o.status = :status)")
    Page<PurchaseOrder> search(@Param("status") OrderStatus status, Pageable pageable);

    @Query("""
            select o from PurchaseOrder o
            join fetch o.request r join fetch r.requester join fetch r.department join fetch o.vendor
            where o.id = :id
            """)
    Optional<PurchaseOrder> findDetail(@Param("id") Long id);
}
