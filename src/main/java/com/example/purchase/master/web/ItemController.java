package com.example.purchase.master.web;

import com.example.purchase.code.service.CodeService;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.master.service.ItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 품목 관리 (SCR-401). 구매팀·관리자 (메뉴 권한) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/master/items")
public class ItemController {

    private final ItemService itemService;
    private final CodeService codeService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String category,
                       @PageableDefault(size = 10) Pageable pageable, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("page", itemService.search(keyword, category, pageable));
        addCodes(model);
        return "master/items/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", new ItemForm());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ItemForm form, BindingResult bindingResult,
                         Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, null);
        }
        try {
            itemService.create(form);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, null);
        }
        redirect.addFlashAttribute("toast", "품목을 등록했습니다.");
        return "redirect:/master/items";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("form", ItemForm.from(itemService.get(id)));
        return formView(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") ItemForm form,
                         BindingResult bindingResult, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, id);
        }
        itemService.update(id, form);
        redirect.addFlashAttribute("toast", "품목을 수정했습니다.");
        return "redirect:/master/items";
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        addCodes(model);
        return "master/items/form";
    }

    private void addCodes(Model model) {
        model.addAttribute("categories", codeService.codeNames("ITEM_CATEGORY"));
        model.addAttribute("units", codeService.codeNames("UNIT"));
    }
}
