package com.example.purchase.code.repository;

import com.example.purchase.code.domain.CommonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommonCodeRepository extends JpaRepository<CommonCode, Long> {

    /** select 박스용: 사용 중인 그룹의 사용 중인 코드만, 정렬 순서대로 */
    @Query("""
            select c from CommonCode c
            where c.group.groupCode = :groupCode and c.used = true and c.group.used = true
            order by c.sortOrder, c.code
            """)
    List<CommonCode> findActiveCodes(@Param("groupCode") String groupCode);
}
