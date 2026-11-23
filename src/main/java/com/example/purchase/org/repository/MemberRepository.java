package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.purchase.org.domain.Role;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByLoginId(String loginId);

    /**
     * 로그인할 때 쓴다. 로그인 사용자 정보에 부서 이름이 필요해서 부서를 함께 읽는다.
     * JPQL의 "join fetch" = 연관된 엔티티를 같은 쿼리에서 한 번에 가져오라는 뜻
     */
    @Query("select m from Member m join fetch m.department where m.loginId = :loginId")
    Optional<Member> findByLoginIdWithDepartment(String loginId);

    /**
     * 결재선 만들 때 (BR-01): 그 부서에서 그 역할을 가진 사용 중인 사람. 직급 높은 사람이 먼저.
     * 직급 이름을 화면에 같이 보여 주려고 position을 fetch join 한다.
     */
    @Query("""
            select m from Member m join fetch m.position p
            where m.department.id = :departmentId and m.role = :role and m.enabled = true
            order by p.rankOrder desc, m.id asc
            """)
    List<Member> findApprovers(@Param("departmentId") Long departmentId, @Param("role") Role role);

    /** 구매팀장 찾기: 부서와 상관없이 그 역할 중 직급이 가장 높은 사람이 먼저 */
    @Query("""
            select m from Member m join fetch m.position p
            where m.role = :role and m.enabled = true
            order by p.rankOrder desc, m.id asc
            """)
    List<Member> findByRoleOrderByRank(@Param("role") Role role);
}
