package com.example.purchase.master.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 거래처 관리 (SCR-402). 상태 자동 확인(국세청 API)은 8단계 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/master/vendors")
public class VendorController {

    private final VendorService vendorService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, @RequestParam(required = false) VendorStatus status,
                       @PageableDefault(size = 10) Pageable pageable, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("statuses", VendorStatus.values());
        model.addAttribute("page", vendorService.search(keyword, status, pageable));
        return "master/vendors/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new VendorForm());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") VendorForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, null);
        }
        try {
            vendorService.create(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, null);
        }
        redirect.addFlashAttribute("toast", "거래처를 등록했습니다.");
        return "redirect:/master/vendors";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", VendorForm.from(vendorService.get(id)));
        return formView(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") VendorForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasFieldErrors("name") || bindingResult.hasFieldErrors("ceoName")) {
            return formView(model, id);
        }
        vendorService.update(id, form);
        redirect.addFlashAttribute("toast", "거래처를 수정했습니다.");
        return "redirect:/master/vendors";
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("statuses", VendorStatus.values());
        return "master/vendors/form";
    }
}
