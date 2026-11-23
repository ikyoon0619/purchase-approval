package com.example.purchase.menu.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.menu.domain.Menu;
import com.example.purchase.menu.repository.MenuRepository;
import com.example.purchase.org.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    private final MenuRepository menuRepository;

    public List<Menu> findAll() {
        return menuRepository.findAllWithRoles();
    }

    /** 이 역할이 볼 수 있는 메뉴 (왼쪽 메뉴 그리기용) */
    public List<MenuItem> menusFor(Role role) {
        return menuRepository.findAllWithRoles().stream()
                .filter(menu -> menu.isAllowed(role))
                .map(MenuItem::from)
                .toList();
    }

    /**
     * 요청 주소에 해당하는 메뉴를 찾는다. 여러 개가 맞으면 가장 긴(구체적인) 주소를 고른다.
     * 예) /admin/members/3/edit → "/admin/members" 메뉴
     */
    public Optional<Menu> findMenuFor(String path) {
        return menuRepository.findAllWithRoles().stream()
                .filter(menu -> matches(menu.getUrl(), path))
                .max(Comparator.comparingInt(menu -> menu.getUrl().length()));
    }

    @Transactional
    @Audited(action = AuditAction.PERMISSION, target = "메뉴 권한")
    public void changeRoles(Map<Long, Set<Role>> rolesByMenuId) {
        rolesByMenuId.forEach((menuId, roles) -> menuRepository.findById(menuId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MENU_NOT_FOUND))
                .changeRoles(roles));
    }

    private boolean matches(String menuUrl, String path) {
        if (menuUrl.equals("/")) {
            return path.equals("/");
        }
        return path.equals(menuUrl) || path.startsWith(menuUrl + "/");
    }

    /** 화면에 넘기는 메뉴 값. 엔티티 대신 필요한 값만 담는다 */
    public record MenuItem(String name, String url) {
        static MenuItem from(Menu menu) {
            return new MenuItem(menu.getName(), menu.getUrl());
        }
    }
}
