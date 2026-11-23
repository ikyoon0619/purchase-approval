package com.example.purchase.approval.repository;

import com.example.purchase.request.domain.ApprovalLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ApprovalLineRepositoryCustom {

    Page<ApprovalLine> searchInbox(Long approverId, InboxTab tab, Pageable pageable);
}
