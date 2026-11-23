package com.example.purchase.approval.web;

import com.example.purchase.approval.repository.InboxTab;
import com.example.purchase.approval.service.ApprovalService;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 결재함(SCR-201)과 결재 처리 버튼. 처리 버튼은 요청서 상세(SCR-103) 화면에 있다.
 * 처리가 끝나면 항상 상세 화면으로 redirect 한다 (PRG 패턴: 새로고침해도 POST가 다시 안 나감).
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/approvals")
    public String inbox(@RequestParam(defaultValue = "PENDING") String tab, @PageableDefault(size = 10) Pageable pageable,
                        @AuthenticationPrincipal LoginUser user, Model model) {
        InboxTab current = parseTab(tab);
        model.addAttribute("tab", current);
        model.addAttribute("tabs", InboxTab.values());
        model.addAttribute("page", approvalService.inbox(user, current, pageable));
        return "approvals/inbox";
    }

    @PostMapping("/requests/{id}/submit")
    public String submit(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        return handle(id, redirect, "상신했습니다. 첫 결재자에게 넘어갔습니다.", () -> approvalService.submit(id, user));
    }

    @PostMapping("/requests/{id}/withdraw")
    public String withdraw(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        return handle(id, redirect, "회수했습니다. 임시저장 상태로 돌아왔습니다.", () -> approvalService.withdraw(id, user));
    }

    @PostMapping("/requests/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) String comment,
                          @AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        return handle(id, redirect, "승인했습니다.", () -> approvalService.approve(id, user, comment));
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(required = false) String comment,
                         @AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        return handle(id, redirect, "반려했습니다.", () -> approvalService.reject(id, user, comment));
    }

    /**
     * 업무 예외와 낙관적 락 충돌을 같은 방식으로 안내한다.
     * 낙관적 락 예외는 서비스 메서드 안이 아니라 "커밋할 때" 터지므로 서비스가 아니라 여기(트랜잭션 바깥)에서 잡는다.
     */
    private String handle(Long id, RedirectAttributes redirect, String okMessage, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("toast", okMessage);
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            log.info("[{}] 요청서 {} 동시 처리 충돌: {}", ErrorCode.ALREADY_PROCESSED.getCode(), id, e.getMessage());
            redirect.addFlashAttribute("error", ErrorCode.ALREADY_PROCESSED.getMessage());
        }
        return "redirect:/requests/" + id;
    }

    private static InboxTab parseTab(String tab) {
        try {
            return InboxTab.valueOf(tab.toUpperCase());
        } catch (IllegalArgumentException e) {
            return InboxTab.PENDING;
        }
    }
}
