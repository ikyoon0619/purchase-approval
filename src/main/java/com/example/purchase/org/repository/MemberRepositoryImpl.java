package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.purchase.org.domain.QDepartment.department;
import static com.example.purchase.org.domain.QMember.member;
import static com.example.purchase.org.domain.QPosition.position;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Member> search(MemberSearchCond cond, Pageable pageable) {
        List<Member> content = baseQuery(cond)
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 건수 쿼리는 join이 필요 없다 (조건이 member 컬럼과 FK만 본다) → 가볍게
        JPAQuery<Long> countQuery = queryFactory
                .select(member.count())
                .from(member)
                .where(conditions(cond));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    @Override
    public List<Member> searchAll(MemberSearchCond cond) {
        return baseQuery(cond).fetch();
    }

    private JPAQuery<Member> baseQuery(MemberSearchCond cond) {
        return queryFactory
                .selectFrom(member)
                .join(member.department, department).fetchJoin()   // ← 이 두 줄이 N+1을 막는다
                .join(member.position, position).fetchJoin()
                .where(conditions(cond))
                .orderBy(department.code.asc(), position.rankOrder.desc(), member.name.asc(), member.id.asc());
    }

    private Predicate[] conditions(MemberSearchCond cond) {
        return new Predicate[]{
                keywordContains(cond.keyword()),
                departmentEq(cond.departmentId()),
                roleEq(cond.role())
        };
    }

    /** 이름 또는 아이디에 검색어가 들어 있으면 */
    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return member.name.contains(keyword).or(member.loginId.contains(keyword));
    }

    private BooleanExpression departmentEq(Long departmentId) {
        return departmentId != null ? member.department.id.eq(departmentId) : null;
    }

    private BooleanExpression roleEq(Role role) {
        return role != null ? member.role.eq(role) : null;
    }
}
