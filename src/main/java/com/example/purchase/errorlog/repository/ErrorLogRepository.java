package com.example.purchase.errorlog.repository;

import com.example.purchase.errorlog.domain.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long> {

    /** 목록에는 스택(TEXT)을 안 읽는다: 엔티티 대신 필요한 컬럼만 */
    @Query(value = """
            select new com.example.purchase.errorlog.service.ErrorLogRow(e.id, e.source, e.loginId, e.exceptionClass, e.message, e.createdAt)
              from ErrorLog e
             where (:keyword is null or e.exceptionClass like concat('%', :keyword, '%') or e.source like concat('%', :keyword, '%'))
             order by e.id desc
            """,
            countQuery = """
            select count(e) from ErrorLog e
             where (:keyword is null or e.exceptionClass like concat('%', :keyword, '%') or e.source like concat('%', :keyword, '%'))
            """)
    Page<com.example.purchase.errorlog.service.ErrorLogRow> search(@Param("keyword") String keyword, Pageable pageable);
}
