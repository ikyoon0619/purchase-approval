package com.example.purchase.menu.web;

import com.example.purchase.menu.domain.Menu;
import com.example.purchase.menu.service.MenuService;
import com.example.purchase.org.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

/** 메뉴 권한 관리 (SCR-505). 메뉴 × 역할 체크박스 표 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/menus")
public class AdminMenuController {

    private static final String PREFIX = "menu_";

    private final MenuService menuService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("menuList", menuService.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/menus/list";
    }

    /**
     * 체크박스 이름이 menu_{id}, 값이 역할 이름이다. 예) menu_3=ADMIN&menu_3=PURCHASING
     * 하나도 체크하지 않은 메뉴는 파라미터가 아예 안 오므로, 화면의 모든 메뉴 id를 hidden(menuIds)으로 함께 보낸다.
     */
    @PostMapping
    public String save(@RequestParam List<Long> menuIds, @RequestParam MultiValueMap<String, String> params,
                       RedirectAttributes redirect) {
        Map<Long, Set<Role>> rolesByMenu = new LinkedHashMap<>();
        for (Long menuId : menuIds) {
            List<String> values = params.getOrDefault(PREFIX + menuId, List.of());
            rolesByMenu.put(menuId, values.stream().map(Role::valueOf).collect(Collectors.toSet()));
        }
        menuService.changeRoles(rolesByMenu);
        redirect.addFlashAttribute("toast", "메뉴 권한을 저장했습니다. 다음 화면부터 적용됩니다.");
        return "redirect:/admin/menus";
    }

    /** 화면에서 체크 여부 확인용 */
    public static boolean has(Menu menu, Role role) {
        return menu.isAllowed(role);
    }
}
