package com.example.purchase.security;

import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 로그인한 사용자 정보. 세션에 저장된다.
 * 엔티티(Member)를 그대로 세션에 넣지 않는 이유: 엔티티는 LAZY 연관관계를 품고 있어서
 * 트랜잭션 밖(다음 요청)에서 꺼내 쓰면 LazyInitializationException이 나고, 직렬화도 어렵다.
 * 그래서 화면에 필요한 값만 복사해 둔다.
 */
@Getter
public class LoginUser implements UserDetails {

    private final Long id;
    private final String loginId;
    private final String password;
    private final String name;
    private final Long departmentId;
    private final String departmentName;
    private final Role role;
    private final boolean enabled;

    public LoginUser(Member member) {
        this.id = member.getId();
        this.loginId = member.getLoginId();
        this.password = member.getPassword();
        this.name = member.getName();
        this.departmentId = member.getDepartment().getId();
        this.departmentName = member.getDepartment().getName();
        this.role = member.getRole();
        this.enabled = member.isEnabled();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
