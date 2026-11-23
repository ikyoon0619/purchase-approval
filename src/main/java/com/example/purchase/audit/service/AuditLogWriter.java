package com.example.purchase.audit.service;

import com.example.purchase.audit.domain.AuditLog;
import com.example.purchase.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 저장은 항상 <b>자기만의 새 트랜잭션(REQUIRES_NEW)</b>으로 한다.
 * 업무 트랜잭션에 끼어서 저장하면, 업무가 실패해 롤백될 때 "실패했다"는 기록까지 같이 사라진다 (docs/troubleshooting/08).
 */
@Service
@RequiredArgsConstructor
public class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(AuditLog log) {
        auditLogRepository.save(log);
    }
}
