package com.example.purchase.menu.web;

import com.example.purchase.menu.service.MenuService;
import com.example.purchase.menu.service.MenuService.MenuItem;
import com.example.purchase.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** 모든 화면의 공통 레이아웃에 필요한 값(왼쪽 메뉴, 현재 주소)을 모델에 넣는다 */
@ControllerAdvice
@RequiredArgsConstructor
public class LayoutModelAdvice {

    private final MenuService menuService;

    @ModelAttribute("menus")
    public List<MenuItem> menus(@AuthenticationPrincipal LoginUser user) {
        return user == null ? List.of() : menuService.menusFor(user.getRole());
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
