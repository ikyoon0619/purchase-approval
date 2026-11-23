package com.example.purchase.audit.repository;

import com.example.purchase.audit.domain.AuditLog;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.purchase.audit.domain.QAuditLog.auditLog;

@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<AuditLog> search(AuditSearchCond c, Pageable pageable) {
        BooleanExpression[] where = {
                StringUtils.hasText(c.loginId()) ? auditLog.loginId.eq(c.loginId().trim()) : null,
                c.action() != null ? auditLog.action.eq(c.action()) : null,
                StringUtils.hasText(c.targetType()) ? auditLog.targetType.eq(c.targetType()) : null,
                StringUtils.hasText(c.targetId()) ? auditLog.targetId.eq(c.targetId().trim()) : null,
                c.success() != null ? auditLog.success.eq(c.success()) : null,
                c.from() != null ? auditLog.createdAt.goe(c.from().atStartOfDay()) : null,
                c.to() != null ? auditLog.createdAt.lt(c.to().plusDays(1).atStartOfDay()) : null};
        List<AuditLog> content = queryFactory.selectFrom(auditLog)
                .where(where)
                .orderBy(auditLog.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
        return PageableExecutionUtils.getPage(content, pageable,
                () -> queryFactory.select(auditLog.count()).from(auditLog).where(where).fetchOne());
    }
}
