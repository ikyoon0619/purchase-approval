package com.example.purchase.inventory.repository;

import com.example.purchase.inventory.domain.InventoryHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {

    /** 최신 이력부터. 관련 발주(있으면)는 left join fetch (N:1) */
    @Query(value = "select h from InventoryHistory h left join fetch h.order where h.item.id = :itemId order by h.id desc",
            countQuery = "select count(h) from InventoryHistory h where h.item.id = :itemId")
    Page<InventoryHistory> findByItem(@Param("itemId") Long itemId, Pageable pageable);

    long countByItemId(Long itemId);
}
