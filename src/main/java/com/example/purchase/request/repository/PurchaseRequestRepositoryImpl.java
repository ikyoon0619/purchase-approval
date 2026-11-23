package com.example.purchase.request.repository;

import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.RequestStatus;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

import static com.example.purchase.master.domain.QVendor.vendor;
import static com.example.purchase.request.domain.QPurchaseRequest.purchaseRequest;

@RequiredArgsConstructor
public class PurchaseRequestRepositoryImpl implements PurchaseRequestRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<PurchaseRequest> searchMine(Long requesterId, RequestSearchCond cond, Pageable pageable) {
        List<PurchaseRequest> content = baseQuery(requesterId, cond)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        JPAQuery<Long> count = queryFactory.select(purchaseRequest.count())
                .from(purchaseRequest)
                .where(conditions(requesterId, cond));
        return PageableExecutionUtils.getPage(content, pageable, count::fetchOne);
    }

    @Override
    public List<PurchaseRequest> searchMineAll(Long requesterId, RequestSearchCond cond) {
        return baseQuery(requesterId, cond).fetch();
    }

    private JPAQuery<PurchaseRequest> baseQuery(Long requesterId, RequestSearchCond cond) {
        return queryFactory.selectFrom(purchaseRequest)
                .join(purchaseRequest.vendor, vendor).fetchJoin()
                .where(conditions(requesterId, cond))
                .orderBy(purchaseRequest.id.desc());
    }

    private Predicate[] conditions(Long requesterId, RequestSearchCond cond) {
        return new Predicate[]{
                purchaseRequest.requester.id.eq(requesterId),
                titleContains(cond.keyword()),
                statusEq(cond.status()),
                createdFrom(cond.from()),
                createdTo(cond.to())
        };
    }

    private BooleanExpression titleContains(String keyword) {
        return StringUtils.hasText(keyword) ? purchaseRequest.title.contains(keyword.trim()) : null;
    }

    private BooleanExpression statusEq(RequestStatus status) {
        return status != null ? purchaseRequest.status.eq(status) : null;
    }

    private BooleanExpression createdFrom(LocalDate from) {
        return from != null ? purchaseRequest.createdAt.goe(from.atStartOfDay()) : null;
    }

    /** 끝 날짜 당일까지 포함: 다음 날 0시 "미만" */
    private BooleanExpression createdTo(LocalDate to) {
        return to != null ? purchaseRequest.createdAt.lt(to.plusDays(1).atStartOfDay()) : null;
    }
}
