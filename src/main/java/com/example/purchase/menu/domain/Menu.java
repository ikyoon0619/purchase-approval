package com.example.purchase.menu.domain;

import com.example.purchase.common.entity.BaseEntity;
import com.example.purchase.org.domain.Role;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 왼쪽 메뉴 한 칸. 어떤 역할이 볼 수 있는지를 menu_role 테이블에 저장한다 (AUTH-03).
 * 같은 정보로 URL 접근도 막는다 ({@link com.example.purchase.security.MenuAuthorizationManager}).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    /** 이 주소로 시작하는 모든 화면에 같은 권한이 적용된다. 예) /admin/members → /admin/members/1/edit 도 포함 */
    @Column(nullable = false, unique = true, length = 100)
    private String url;

    @Column(nullable = false)
    private int sortOrder;

    /** 값 타입 컬렉션: 엔티티가 아닌 값(enum)의 목록을 별도 테이블(menu_role)에 저장한다 */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "menu_role", joinColumns = @JoinColumn(name = "menu_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Set<Role> roles = new HashSet<>();

    public Menu(String name, String url, int sortOrder, Set<Role> roles) {
        this.name = name;
        this.url = url;
        this.sortOrder = sortOrder;
        this.roles = new HashSet<>(roles);
    }

    public boolean isAllowed(Role role) {
        return roles.contains(role);
    }

    /** 관리자 메뉴는 관리자 권한을 뺄 수 없게 막는다 (실수로 아무도 못 들어가는 일을 방지) */
    public void changeRoles(Set<Role> newRoles) {
        Set<Role> next = newRoles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(newRoles);
        if (url.startsWith("/admin")) {
            next.add(Role.ADMIN);
        }
        roles.clear();
        roles.addAll(next);
    }
}
