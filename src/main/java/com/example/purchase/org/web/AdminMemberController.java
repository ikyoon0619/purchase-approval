package com.example.purchase.org.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.MemberSearchCond;
import com.example.purchase.org.service.DepartmentService;
import com.example.purchase.org.service.MemberService;
import com.example.purchase.org.service.PositionService;
import com.example.purchase.common.excel.ExcelColumn;
import com.example.purchase.common.excel.ExcelWriter;
import com.example.purchase.org.domain.Member;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 사용자 관리 (SCR-501) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/admin/members")
public class AdminMemberController {

    private final MemberService memberService;
    private final DepartmentService departmentService;
    private final PositionService positionService;

    @GetMapping
    public String list(@ModelAttribute("cond") MemberSearchCond cond,
                       @PageableDefault(size = 10) Pageable pageable, Model model) {
        model.addAttribute("page", memberService.search(cond, pageable));
        model.addAttribute("departments", departmentService.activeDepartments());
        model.addAttribute("roles", Role.values());
        return "admin/members/list";
    }

    /** 지금 검색 조건 그대로 전체를 엑셀로 내려받는다 */
    @GetMapping("/excel")
    public void excel(@ModelAttribute("cond") MemberSearchCond cond, HttpServletResponse response) throws IOException {
        List<Member> members = memberService.searchAll(cond);

        String fileName = "사용자목록_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        // 한글 파일명: filename*=UTF-8''%EC%82%AC... 형식으로 보내야 브라우저에서 안 깨진다
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());

        ExcelWriter.write("사용자", EXCEL_COLUMNS, members, response.getOutputStream());
    }

    private static final List<ExcelColumn<Member>> EXCEL_COLUMNS = List.of(
            new ExcelColumn<>("아이디", Member::getLoginId, 8),
            new ExcelColumn<>("이름", Member::getName, 6),
            new ExcelColumn<>("이메일", Member::getEmail, 12),
            new ExcelColumn<>("부서", m -> m.getDepartment().getName(), 8),   // fetch join으로 읽어 둬서 추가 쿼리 없음
            new ExcelColumn<>("직급", m -> m.getPosition().getName(), 5),
            new ExcelColumn<>("역할", m -> m.getRole().getLabel(), 5),
            new ExcelColumn<>("사용", Member::isEnabled, 3),
            new ExcelColumn<>("등록일", Member::getCreatedAt, 9));

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
