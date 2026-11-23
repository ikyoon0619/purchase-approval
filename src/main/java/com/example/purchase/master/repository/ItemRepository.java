package com.example.purchase.master.repository;

import com.example.purchase.master.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    boolean existsByCode(String code);

    /** 품목 관리 목록. 조건이 단순해서 QueryDSL 대신 JPQL로 ("값이 없으면 조건 무시" 패턴) */
    @Query("""
            select i from Item i
            where (:keyword is null or i.name like concat('%', :keyword, '%') or i.code like concat('%', :keyword, '%'))
              and (:category is null or i.categoryCode = :category)
            order by i.code
            """)
    Page<Item> search(@Param("keyword") String keyword, @Param("category") String category, Pageable pageable);

    /** 요청서 작성 화면의 품목 고르기 */
    List<Item> findByUsedTrueOrderByCategoryCodeAscNameAsc();
}
