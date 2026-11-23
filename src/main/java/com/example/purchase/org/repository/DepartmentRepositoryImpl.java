package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Department;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.purchase.org.domain.QDepartment.department;

/**
 * 이름이 "리포지토리 인터페이스 + Impl"이면 Spring Data가 알아서 합쳐 준다.
 * QDepartment는 컴파일할 때 QueryDSL이 Department를 보고 만들어 주는 클래스다.
 */
@RequiredArgsConstructor
public class DepartmentRepositoryImpl implements DepartmentRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Department> search(String keyword, Boolean used) {
        return queryFactory
                .selectFrom(department)
                .where(nameContains(keyword), usedEq(used)) // null인 조건은 무시된다
                .orderBy(department.code.asc())
                .fetch();
    }

    private BooleanExpression nameContains(String keyword) {
        return StringUtils.hasText(keyword) ? department.name.contains(keyword) : null;
    }

    private BooleanExpression usedEq(Boolean used) {
        return used != null ? department.used.eq(used) : null;
    }
}
