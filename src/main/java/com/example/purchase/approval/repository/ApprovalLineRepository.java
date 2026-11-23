package com.example.purchase.approval.repository;

import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.LineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApprovalLineRepository extends JpaRepository<ApprovalLine, Long>, ApprovalLineRepositoryCustom {

    /** 왼쪽 메뉴 "결재함 (2)" 숫자. 결재 차례(PENDING)는 문서가 결재중일 때만 있으므로 조건 하나로 충분 */
    long countByApproverIdAndStatus(Long approverId, LineStatus status);

    /**
     * 결재 지연 알림 대상 (APV-05): limit 이전부터 결재 차례인 단계.
     * pendingAt은 9단계에서 생긴 컬럼이라 옛 행은 비어 있어 created_at(상신 시각)으로 대신한다.
     * 알림 문구에 문서번호·제목·결재자가 필요해서 같이 읽는다 (N+1 방지).
     */
    @Query("""
            select l from ApprovalLine l
              join fetch l.request r
              join fetch l.approver
             where l.status = com.example.purchase.request.domain.LineStatus.PENDING
               and coalesce(l.pendingAt, l.createdAt) < :limit
             order by l.id
            """)
    List<ApprovalLine> findPendingSince(@Param("limit") LocalDateTime limit);
}
