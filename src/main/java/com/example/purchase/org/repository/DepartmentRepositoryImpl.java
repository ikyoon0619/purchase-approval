package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.QDepartment;
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
        QDepartment parent = new QDepartment("parent"); // 같은 테이블을 한 번 더 join하므로 별칭을 따로 둔다
        return queryFactory
                .selectFrom(department)
                // 상위 부서 이름을 화면에서 쓴다. open-in-view를 끈 뒤에는 여기서 같이 읽어 두지 않으면
                // 검색으로 본부가 결과에서 빠질 때 LazyInitializationException이 난다 (docs/troubleshooting/03)
                .leftJoin(department.parent, parent).fetchJoin()
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
