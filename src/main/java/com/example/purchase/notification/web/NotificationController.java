package com.example.purchase.notification.web;

import com.example.purchase.notification.service.NotificationRow;
import com.example.purchase.notification.service.NotificationService;
import com.example.purchase.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 알림 목록. 메뉴에 없는 주소라 로그인한 사람은 누구나 (자기 알림만 보인다) */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(@AuthenticationPrincipal LoginUser user, Pageable pageable, Model model) {
        Page<NotificationRow> page = notificationService.list(user.getId(), pageable);
        model.addAttribute("page", page);
        return "notifications/list";
    }

    /** 알림 클릭: 읽음 처리 후 관련 화면으로 (읽음은 상태 변경이라 GET이 아니라 POST) */
    @PostMapping("/{id}/open")
    public String open(@PathVariable Long id, @AuthenticationPrincipal LoginUser user) {
        return "redirect:" + notificationService.open(id, user.getId());
    }

    @PostMapping("/read-all")
    public String readAll(@AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        int n = notificationService.readAll(user.getId());
        ra.addFlashAttribute("toast", "알림 " + n + "건을 읽음으로 바꿨습니다.");
        return "redirect:/notifications";
    }
}
