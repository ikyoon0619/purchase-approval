package com.example.purchase.inventory.repository;

import com.example.purchase.inventory.domain.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 재고를 바꾸기 전에 그 행을 잠그고 읽는다: SELECT ... FOR UPDATE.
     * 같은 품목을 동시에 입고·출고하면 뒤에 온 트랜잭션은 앞 트랜잭션이 커밋할 때까지 여기서 기다린다.
     * 기다리는 시간 한도는 DB 설정(MySQL innodb_lock_wait_timeout, application.yml의 접속 주소에서 5초로 줄임)이고,
     * 넘기면 PessimisticLockingFailureException → 화면에 "잠시 후 다시 시도".
     * (jakarta.persistence.lock.timeout 힌트는 MySQL에서 0(NOWAIT) 말고는 SQL에 반영되지 않아서 쓰지 않았다)
     * (fetch join을 붙이지 않는다: 같이 읽은 행까지 잠근다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Inventory v where v.item.id = :itemId")
    Optional<Inventory> findByItemIdForUpdate(@Param("itemId") Long itemId);

    /** 락 없이 읽기. 화면 조회와 문제 재현 테스트용 */
    @Query("select v from Inventory v where v.item.id = :itemId")
    Optional<Inventory> findByItemId(@Param("itemId") Long itemId);

    /** 재고 현황 (INV-01). 품목과 N:1이라 fetch join 해도 페이징 안전 */
    @Query(value = """
            select v from Inventory v join fetch v.item i
            where (:keyword is null or i.name like concat('%', :keyword, '%') or i.code like concat('%', :keyword, '%'))
              and (:onlyInStock = false or v.quantity > 0)
            order by i.code asc
            """,
            countQuery = """
            select count(v) from Inventory v join v.item i
            where (:keyword is null or i.name like concat('%', :keyword, '%') or i.code like concat('%', :keyword, '%'))
              and (:onlyInStock = false or v.quantity > 0)
            """)
    Page<Inventory> search(@Param("keyword") String keyword, @Param("onlyInStock") boolean onlyInStock, Pageable pageable);

    /** 재고 행이 없는 품목에 0으로 만들어 준다 (기존 DB에 품목만 있던 경우) */
    @Query("select i.id from Item i where not exists (select 1 from Inventory v where v.item = i)")
    java.util.List<Long> findItemIdsWithoutInventory();
}
