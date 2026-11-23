package com.example.purchase.code.repository;

import com.example.purchase.code.domain.CodeGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodeGroupRepository extends JpaRepository<CodeGroup, String>, CodeGroupRepositoryCustom {

    /** 상세 화면: 그룹 하나와 코드를 한 번에. 한 건이라 페이징이 없으므로 컬렉션 fetch join을 써도 된다 */
    @Query("select g from CodeGroup g left join fetch g.codes where g.groupCode = :groupCode")
    Optional<CodeGroup> findWithCodes(@Param("groupCode") String groupCode);
}
