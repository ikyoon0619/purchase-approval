package com.example.purchase.org.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자. 부서·직급과 N:1 관계다.
 * 연관관계는 모두 LAZY로 두고, 같이 필요한 화면에서만 fetch join으로 한 번에 읽는다 (N+1 방지).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String loginId;

    /** BCrypt 해시. 평문은 저장하지 않는다 (AUTH-04) */
    @Column(nullable = false, length = 100)
    private String password;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 100)
    private String email;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id")
    private Position position;

    @Enumerated(EnumType.STRING) // ORDINAL(숫자)로 두면 enum 순서를 바꾸는 순간 기존 데이터 뜻이 바뀐다
    // MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다 (troubleshooting 09)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    public Member(String loginId, String encodedPassword, String name, String email,
                  Department department, Position position, Role role) {
        this.loginId = loginId;
        this.password = encodedPassword;
        this.name = name;
        this.email = email;
        this.department = department;
        this.position = position;
        this.role = role;
    }

    public void update(String name, String email, Department department, Position position,
                       Role role, boolean enabled) {
        this.name = name;
        this.email = email;
        this.department = department;
        this.position = position;
        this.role = role;
        this.enabled = enabled;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}
