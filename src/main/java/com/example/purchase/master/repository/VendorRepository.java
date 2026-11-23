package com.example.purchase.master.repository;

import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    boolean existsByBizNo(String bizNo);

    @Query("""
            select v from Vendor v
            where (:keyword is null or v.name like concat('%', :keyword, '%') or v.bizNo like concat('%', :keyword, '%'))
              and (:status is null or v.status = :status)
            order by v.name
            """)
    Page<Vendor> search(@Param("keyword") String keyword, @Param("status") VendorStatus status, Pageable pageable);

    /** 요청서에서 고를 수 있는 거래처 (BR-09) */
    List<Vendor> findByUsedTrueAndStatusInOrderByNameAsc(Collection<VendorStatus> statuses);
}
