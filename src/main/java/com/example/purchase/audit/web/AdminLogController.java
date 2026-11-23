package com.example.purchase.audit.web;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.repository.AuditSearchCond;
import com.example.purchase.audit.service.AuditLogService;
import com.example.purchase.errorlog.service.ErrorLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/** 관리자: 감사 로그, 에러 로그 (/admin/** 이라 ADMIN만) */
@Controller
@RequiredArgsConstructor
public class AdminLogController {

    private final AuditLogService auditLogService;
    private final ErrorLogService errorLogService;

    @GetMapping("/admin/audit-logs")
    public String auditLogs(@ModelAttribute("cond") AuditSearchCond cond, @PageableDefault(size = 20) Pageable pageable, Model model) {
        model.addAttribute("page", auditLogService.search(cond, pageable));
        model.addAttribute("actions", AuditAction.values());
        return "admin/logs/audit";
    }

    @GetMapping("/admin/error-logs")
    public String errorLogs(@RequestParam(required = false) String keyword, @PageableDefault(size = 20) Pageable pageable, Model model) {
        model.addAttribute("page", errorLogService.search(keyword, pageable));
        model.addAttribute("keyword", keyword);
        return "admin/logs/errors";
    }

    @GetMapping("/admin/error-logs/{id}")
    public String errorLog(@PathVariable Long id, Model model) {
        model.addAttribute("log", errorLogService.get(id));
        return "admin/logs/error-detail";
    }
}
