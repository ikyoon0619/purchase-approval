package com.example.purchase.org.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부서. 본부(parent = null) 아래에 팀이 있는 2단 구조.
 * 결재선을 만들 때 "기안자 팀의 팀장 → 상위 본부의 부서장" 순서로 찾는다 (BR-01).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA용 기본 생성자. 밖에서 new Department()로 빈 객체를 못 만들게 막는다
public class Department extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    /** 상위 부서. LAZY: 부서를 읽을 때 상위 부서까지 바로 읽지 않고, 실제로 쓸 때 읽는다 */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Department parent;

    @Column(name = "use_yn", nullable = false)
    private boolean used = true;

    private Department(String code, String name, Department parent) {
        this.code = code;
        this.name = name;
        this.parent = parent;
    }

    /** 본부 만들기 */
    public static Department division(String code, String name) {
        return new Department(code, name, null);
    }

    /** 본부 아래 팀 만들기 */
    public static Department team(String code, String name, Department division) {
        return new Department(code, name, division);
    }

    public boolean isDivision() {
        return parent == null;
    }
}
