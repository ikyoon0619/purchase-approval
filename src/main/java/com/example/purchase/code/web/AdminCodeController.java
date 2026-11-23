package com.example.purchase.code.web;

import com.example.purchase.code.domain.CodeGroup;
import com.example.purchase.code.service.CodeService;
import com.example.purchase.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 공통코드 관리 (SCR-504) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/codes")
public class AdminCodeController {

    private final CodeService codeService;

    /** 그룹 목록. ?keyword=단위&page=2 */
    @GetMapping
    public String list(@RequestParam(required = false) String keyword,
                       @PageableDefault(size = 10) Pageable pageable, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("page", codeService.search(keyword, pageable));
        return "admin/codes/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new CodeGroupForm());
        return "admin/codes/new";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") CodeGroupForm form, BindingResult bindingResult,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return "admin/codes/new";
        }
        try {
            codeService.createGroup(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return "admin/codes/new";
        }
        redirect.addFlashAttribute("toast", "코드 그룹을 만들었습니다. 이제 코드를 추가하세요.");
        return "redirect:/admin/codes/" + form.getGroupCode();
    }

    /** 그룹 상세: 그룹 수정 + 코드 목록 + 코드 추가 */
    @GetMapping("/{groupCode}")
    public String detail(@PathVariable String groupCode, Model model) {
        CodeGroup group = codeService.getWithCodes(groupCode);
        model.addAttribute("group", group);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", CodeGroupForm.from(group));
        }
        model.addAttribute("newCode", new CommonCodeForm());
        return "admin/codes/detail";
    }

    @PostMapping("/{groupCode}")
    public String update(@PathVariable String groupCode, @Valid @ModelAttribute("form") CodeGroupForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        form.setGroupCode(groupCode); // 그룹 코드는 바꿀 수 없다
        if (bindingResult.hasFieldErrors("name")) {
            return detail(groupCode, model);
        }
        codeService.updateGroup(groupCode, form);
        redirect.addFlashAttribute("toast", "그룹 정보를 저장했습니다.");
        return "redirect:/admin/codes/" + groupCode;
    }

    @PostMapping("/{groupCode}/codes")
    public String addCode(@PathVariable String groupCode, @Valid @ModelAttribute CommonCodeForm form,
                          BindingResult bindingResult, RedirectAttributes redirect) {
        if (!StringUtils.hasText(form.getCode())) {
            bindingResult.rejectValue("code", "required", "코드를 입력해 주세요.");
        }
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/codes/" + groupCode;
        }
        try {
            codeService.addCode(groupCode, form);
            redirect.addFlashAttribute("toast", "코드 " + form.getCode() + "을(를) 추가했습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes/" + groupCode;
    }

    @PostMapping("/{groupCode}/codes/{codeId}")
    public String updateCode(@PathVariable String groupCode, @PathVariable Long codeId,
                             @Valid @ModelAttribute CommonCodeForm form, BindingResult bindingResult,
                             RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/admin/codes/" + groupCode;
        }
        codeService.updateCode(groupCode, codeId, form);
        redirect.addFlashAttribute("toast", "코드를 저장했습니다.");
        return "redirect:/admin/codes/" + groupCode;
    }
}
