package com.example.purchase.approval.repository;

import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.LineStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long>, ApprovalLineRepositoryCustom {

    /** 왼쪽 메뉴 "결재함 (2)" 숫자. 결재 차례(PENDING)는 문서가 결재중일 때만 있으므로 조건 하나로 충분 */
    long countByApproverIdAndStatus(Long approverId, LineStatus status);
}
