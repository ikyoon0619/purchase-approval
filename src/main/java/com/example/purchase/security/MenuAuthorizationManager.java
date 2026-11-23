package com.example.purchase.security;

import com.example.purchase.menu.domain.Menu;
import com.example.purchase.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * URL 접근 권한을 메뉴 권한(menu_role)으로 판단한다.
 * 관리자가 메뉴 권한 화면에서 체크를 바꾸면, 메뉴가 보이는 것뿐 아니라 주소를 직접 쳐서 들어가는 것도 함께 막힌다.
 * - 메뉴에 걸린 주소: 그 메뉴를 볼 수 있는 역할만 통과
 * - 메뉴에 없는 주소: 로그인만 했으면 통과
 */
@Component
@RequiredArgsConstructor
public class MenuAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final MenuService menuService;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof LoginUser user)) {
            return new AuthorizationDecision(false);
        }
        String path = context.getRequest().getRequestURI().substring(context.getRequest().getContextPath().length());
        Optional<Menu> menu = menuService.findMenuFor(path);
        return new AuthorizationDecision(menu.map(m -> m.isAllowed(user.getRole())).orElse(true));
    }
}
