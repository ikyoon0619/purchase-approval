package com.example.purchase.support;

import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 테스트용 조직도를 만드는 도우미. 테스트마다 같은 준비 코드를 반복하지 않으려고 둔다 */
public class OrgFixture {

    public static final String PASSWORD = "pass1234";
    private static final String ENCODED = new BCryptPasswordEncoder().encode(PASSWORD);

    private final EntityManager em;

    public OrgFixture(EntityManager em) {
        this.em = em;
    }

    public Department division(String code, String name) {
        Department d = Department.division(code, name);
        em.persist(d);
        return d;
    }

    public Department team(String code, String name, Department division) {
        Department d = Department.team(code, name, division);
        em.persist(d);
        return d;
    }

    public Position position(String name, int rank) {
        Position p = new Position(name, rank);
        em.persist(p);
        return p;
    }

    public Member member(String loginId, String name, Department dept, Position pos, Role role) {
        Member m = new Member(loginId, ENCODED, name, loginId + "@example.com", dept, pos, role);
        em.persist(m);
        return m;
    }
}
