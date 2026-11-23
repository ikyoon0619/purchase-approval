package com.example.purchase.org.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.org.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 부서 관리 (SCR-502). /admin/** 은 SecurityConfig에서 ADMIN만 들어올 수 있다 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/departments")
public class AdminDepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, Model model) {
        model.addAttribute("departments", departmentService.search(keyword, null));
        model.addAttribute("keyword", keyword);
        return "admin/departments/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new DepartmentForm());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") DepartmentForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, null);
        }
        try {
            departmentService.create(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage()); // 폼 위에 오류 메시지로 보여 준다
            return formView(model, null);
        }
        redirect.addFlashAttribute("toast", "부서를 등록했습니다.");
        return "redirect:/admin/departments";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", DepartmentForm.from(departmentService.get(id)));
        return formView(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") DepartmentForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, id);
        }
        try {
            departmentService.update(id, form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, id);
        }
        redirect.addFlashAttribute("toast", "부서를 수정했습니다.");
        return "redirect:/admin/departments";
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("divisions", departmentService.divisions());
        return "admin/departments/form";
    }
}
