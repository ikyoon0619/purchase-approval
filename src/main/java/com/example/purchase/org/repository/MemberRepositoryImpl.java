package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.purchase.org.domain.QDepartment.department;
import static com.example.purchase.org.domain.QMember.member;
import static com.example.purchase.org.domain.QPosition.position;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Member> search(MemberSearchCond cond) {
        return queryFactory
                .selectFrom(member)
                .join(member.department, department).fetchJoin()   // ← 이 두 줄이 N+1을 막는다
                .join(member.position, position).fetchJoin()
                .where(
                        keywordContains(cond.keyword()),
                        departmentEq(cond.departmentId()),
                        roleEq(cond.role())
                )
                .orderBy(department.code.asc(), position.rankOrder.desc(), member.name.asc())
                .fetch();
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
