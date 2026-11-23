package com.example.purchase.approval.service;

import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.LineStatus;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 결재함 한 줄. 트랜잭션 안에서 만든다 (open-in-view: false) */
public record InboxRow(Long requestId, String requestNo, String title, String requesterName, String departmentName,
                       BigDecimal totalAmount, RequestStatus requestStatus, String stepName, LineStatus lineStatus,
                       LocalDateTime submittedAt, LocalDateTime processedAt) {

    public static InboxRow from(ApprovalLine line) {
        PurchaseRequest r = line.getRequest();
        return new InboxRow(r.getId(), r.getRequestNo(), r.getTitle(), r.getRequester().getName(),
                r.getDepartment().getName(), r.getTotalAmount(), r.getStatus(), line.getStepName(),
                line.getStatus(), r.getSubmittedAt(), line.getProcessedAt());
    }
}
