package com.example.purchase.approval.repository;

import com.example.purchase.request.domain.ApprovalLine;
import com.example.purchase.request.domain.LineStatus;
import com.example.purchase.request.domain.RequestStatus;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;

import java.util.List;

import static com.example.purchase.org.domain.QDepartment.department;
import static com.example.purchase.org.domain.QMember.member;
import static com.example.purchase.request.domain.QApprovalLine.approvalLine;
import static com.example.purchase.request.domain.QPurchaseRequest.purchaseRequest;

/**
 * 결재함 목록. 결재선 한 줄(approval_line)이 목록 한 줄이다.
 * 목록에 문서 제목·기안자·부서가 필요하므로 N:1 연관만 fetch join 한다 (1:N이 없으니 페이징해도 안전).
 */
@RequiredArgsConstructor
public class ApprovalLineRepositoryImpl implements ApprovalLineRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<ApprovalLine> searchInbox(Long approverId, InboxTab tab, Pageable pageable) {
        List<ApprovalLine> content = queryFactory.selectFrom(approvalLine)
                .join(approvalLine.request, purchaseRequest).fetchJoin()
                .join(purchaseRequest.requester, member).fetchJoin()
                .join(purchaseRequest.department, department).fetchJoin()
                .where(approvalLine.approver.id.eq(approverId), tabCondition(tab))
                .orderBy(order(tab), approvalLine.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        JPAQuery<Long> count = queryFactory.select(approvalLine.count())
                .from(approvalLine)
                .join(approvalLine.request, purchaseRequest)
                .where(approvalLine.approver.id.eq(approverId), tabCondition(tab));
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    private BooleanExpression tabCondition(InboxTab tab) {
        return switch (tab) {
            // 지금 내 차례
            case PENDING -> approvalLine.status.eq(LineStatus.PENDING);
            // 내가 승인·반려한 것
            case DONE -> approvalLine.status.in(LineStatus.APPROVED, LineStatus.REJECTED);
            // 아직 결재중인 문서 중 내가 이미 승인했거나 내 차례가 아직 안 온 것 (이번 회차만)
            case PROGRESS -> purchaseRequest.status.eq(RequestStatus.IN_APPROVAL)
                    .and(approvalLine.round.eq(purchaseRequest.submitRound))
                    .and(approvalLine.status.in(LineStatus.APPROVED, LineStatus.WAITING));
        };
    }

    private OrderSpecifier<?> order(InboxTab tab) {
        return switch (tab) {
            case PENDING -> purchaseRequest.submittedAt.asc();   // 오래 기다린 문서부터
            case DONE -> approvalLine.processedAt.desc();         // 최근 처리한 것부터
            case PROGRESS -> purchaseRequest.submittedAt.desc();
        };
    }
}
