package com.example.purchase.org.repository;

import com.example.purchase.org.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long>, MemberRepositoryCustom {

    boolean existsByLoginId(String loginId);

    /**
     * 로그인할 때 쓴다. 로그인 사용자 정보에 부서 이름이 필요해서 부서를 함께 읽는다.
     * JPQL의 "join fetch" = 연관된 엔티티를 같은 쿼리에서 한 번에 가져오라는 뜻
     */
    @Query("select m from Member m join fetch m.department where m.loginId = :loginId")
    Optional<Member> findByLoginIdWithDepartment(String loginId);
}
