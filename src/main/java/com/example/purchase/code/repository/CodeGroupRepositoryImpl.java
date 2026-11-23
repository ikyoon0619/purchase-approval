package com.example.purchase.code.repository;

import com.example.purchase.code.domain.CodeGroup;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.example.purchase.code.domain.QCodeGroup.codeGroup;

@RequiredArgsConstructor
public class CodeGroupRepositoryImpl implements CodeGroupRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<CodeGroup> search(String keyword, Pageable pageable) {
        // 1) 내용 쿼리: DB에서 limit/offset으로 필요한 10건만 가져온다
        List<CodeGroup> content = queryFactory
                .selectFrom(codeGroup)
                .where(keywordContains(keyword))
                .orderBy(codeGroup.groupCode.asc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();

        // 2) 전체 건수 쿼리: 마지막 페이지처럼 필요 없을 때는 PageableExecutionUtils가 생략한다
        JPAQuery<Long> countQuery = queryFactory
                .select(codeGroup.count())
                .from(codeGroup)
                .where(keywordContains(keyword));

        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private BooleanExpression keywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return codeGroup.groupCode.containsIgnoreCase(keyword).or(codeGroup.name.contains(keyword));
    }
}
