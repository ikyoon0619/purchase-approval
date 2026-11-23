package com.example.purchase.audit.service;

import com.example.purchase.audit.domain.AuditLog;
import com.example.purchase.audit.repository.AuditLogRepository;
import com.example.purchase.audit.repository.AuditSearchCond;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public Page<AuditLog> search(AuditSearchCond cond, Pageable pageable) {
        return auditLogRepository.search(cond, pageable);
    }
}
