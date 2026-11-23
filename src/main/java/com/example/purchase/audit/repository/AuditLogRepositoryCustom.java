package com.example.purchase.audit.repository;

import com.example.purchase.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogRepositoryCustom {

    Page<AuditLog> search(AuditSearchCond cond, Pageable pageable);
}
