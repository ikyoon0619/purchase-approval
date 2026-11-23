package com.example.purchase.config;

import com.example.purchase.menu.domain.Menu;
import com.example.purchase.menu.repository.MenuRepository;
import com.example.purchase.org.domain.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

/**
 * 기본 메뉴를 넣는다. 모든 환경에서 돈다.
 * 주소(url) 기준으로 없는 메뉴만 추가하므로, 단계가 진행되며 메뉴가 늘어나도 기존 권한 설정은 그대로 둔다.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class MenuInitializer implements ApplicationRunner {

    private static final EnumSet<Role> ALL = EnumSet.allOf(Role.class);
    private static final EnumSet<Role> ADMIN = EnumSet.of(Role.ADMIN);

    private final MenuRepository menuRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Menu> defaults = List.of(
                new Menu("대시보드", "/", 10, ALL),
                new Menu("구매요청", "/requests", 20, ALL),
                new Menu("결재함", "/approvals", 30, EnumSet.of(Role.TEAM_LEADER, Role.DEPT_HEAD, Role.PURCHASING, Role.ADMIN)),
                new Menu("품목 관리", "/master/items", 500, EnumSet.of(Role.PURCHASING, Role.ADMIN)),
                new Menu("거래처 관리", "/master/vendors", 510, EnumSet.of(Role.PURCHASING, Role.ADMIN)),
                new Menu("사용자 관리", "/admin/members", 900, ADMIN),
                new Menu("부서 관리", "/admin/departments", 910, ADMIN),
                new Menu("직급 관리", "/admin/positions", 920, ADMIN),
                new Menu("메뉴 권한", "/admin/menus", 930, ADMIN),
                new Menu("공통코드 관리", "/admin/codes", 940, ADMIN)
        );
        int added = 0;
        for (Menu menu : defaults) {
            if (menuRepository.findByUrl(menu.getUrl()).isEmpty()) {
                menuRepository.save(menu);
                added++;
            }
        }
        if (added > 0) {
            log.info("기본 메뉴 {}개를 추가했습니다.", added);
        }
    }
}
