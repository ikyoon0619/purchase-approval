package com.example.purchase.home;

import com.example.purchase.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** 첫 화면(대시보드). 결재 대기 건수는 공통 레이아웃(LayoutModelAdvice)이 이미 넣어 준다 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String home(@AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("d", dashboardService.load(user));
        return "home";
    }
}
