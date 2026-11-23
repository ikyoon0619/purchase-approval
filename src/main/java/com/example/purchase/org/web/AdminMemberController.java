package com.example.purchase.org.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.MemberSearchCond;
import com.example.purchase.org.service.DepartmentService;
import com.example.purchase.org.service.MemberService;
import com.example.purchase.org.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 사용자 관리 (SCR-501) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final MemberService memberService;
    private final DepartmentService departmentService;
    private final PositionService positionService;

    @GetMapping
    public String list(@ModelAttribute("cond") MemberSearchCond cond, Model model) {
        model.addAttribute("members", memberService.search(cond));
        model.addAttribute("departments", departmentService.activeDepartments());
        model.addAttribute("roles", Role.values());
        return "admin/members/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new MemberForm());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") MemberForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, null);
        }
        try {
            memberService.create(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, null);
        }
        redirect.addFlashAttribute("toast", "사용자를 등록했습니다.");
        return "redirect:/admin/members";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", MemberForm.from(memberService.get(id)));
        return formView(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") MemberForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, id);
        }
        try {
            memberService.update(id, form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, id);
        }
        redirect.addFlashAttribute("toast", "사용자 정보를 수정했습니다.");
        return "redirect:/admin/members";
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("departments", departmentService.activeDepartments());
        model.addAttribute("positions", positionService.findAll());
        model.addAttribute("roles", Role.values());
        return "admin/members/form";
    }
}
