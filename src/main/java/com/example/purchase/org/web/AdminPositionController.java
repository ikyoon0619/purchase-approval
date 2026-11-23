package com.example.purchase.org.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.org.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 직급 관리 (SCR-503) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/positions")
public class AdminPositionController {

    private final PositionService positionService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("positions", positionService.findAll());
        return "admin/positions/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new PositionForm());
        model.addAttribute("id", null);
        return "admin/positions/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") PositionForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("id", null);
        if (bindingResult.hasErrors()) {
            return "admin/positions/form";
        }
        try {
            positionService.create(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return "admin/positions/form";
        }
        redirect.addFlashAttribute("toast", "직급을 등록했습니다.");
        return "redirect:/admin/positions";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", PositionForm.from(positionService.get(id)));
        model.addAttribute("id", id);
        return "admin/positions/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") PositionForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        model.addAttribute("id", id);
        if (bindingResult.hasErrors()) {
            return "admin/positions/form";
        }
        try {
            positionService.update(id, form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return "admin/positions/form";
        }
        redirect.addFlashAttribute("toast", "직급을 수정했습니다.");
        return "redirect:/admin/positions";
    }
}
