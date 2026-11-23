package com.example.purchase.master.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.service.VendorService;
import com.example.purchase.master.service.VendorStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 거래처 관리 (SCR-402). 사업자 상태는 국세청 API로 확인 (BR-09) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/master/vendors")
public class VendorController {

    private final VendorService vendorService;
    private final VendorStatusService statusService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, @RequestParam(required = false) VendorStatus status,
                       @PageableDefault(size = 10) Pageable pageable, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", status);
        model.addAttribute("statuses", VendorStatus.values());
        model.addAttribute("page", vendorService.search(keyword, status, pageable));
        model.addAttribute("apiEnabled", statusService.isApiEnabled());
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
        Long id;
        try {
            id = vendorService.create(form);   // 저장 트랜잭션은 여기서 끝난다
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, null);
        }
        if (statusService.isApiEnabled()) {
            VendorStatusService.CheckResult r = statusService.refresh(id); // 트랜잭션 밖에서 국세청 확인
            redirect.addFlashAttribute("toast", "거래처를 등록했습니다. 국세청 확인: " + r.status().getLabel() + " (" + r.message() + ")");
        } else {
            redirect.addFlashAttribute("toast", "거래처를 등록했습니다.");
        }
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

    /** 한 곳 상태 확인 */
    @PostMapping("/{id}/check-status")
    public String checkStatus(@PathVariable Long id, RedirectAttributes redirect) {
        VendorStatusService.CheckResult r = statusService.refresh(id);
        redirect.addFlashAttribute(r.checked() ? "toast" : "error",
                r.vendorName() + ": " + r.status().getLabel() + " · " + r.message());
        return "redirect:/master/vendors";
    }

    /** 사용 중인 거래처 전체 확인 (100곳씩 나눠 호출) */
    @PostMapping("/check-all")
    public String checkAll(RedirectAttributes redirect) {
        VendorStatusService.Summary s = statusService.refreshAll();
        if (s.total() == 0 && s.error() != null) {
            redirect.addFlashAttribute("error", s.error());
        } else {
            redirect.addFlashAttribute(s.error() == null ? "toast" : "error",
                    "전체 " + s.total() + "곳 확인: 확인 " + s.checked() + ", 확인 안 됨 " + s.unknown() + ", 상태 바뀜 " + s.changed()
                            + (s.error() == null ? "" : " (" + s.error() + ")"));
        }
        return "redirect:/master/vendors";
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("statuses", VendorStatus.values());
        return "master/vendors/form";
    }
}
