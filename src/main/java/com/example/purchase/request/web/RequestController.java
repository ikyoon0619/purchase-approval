package com.example.purchase.request.web;

import com.example.purchase.code.service.CodeService;
import com.example.purchase.common.excel.ExcelColumn;
import com.example.purchase.common.excel.ExcelWriter;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.master.service.ItemService;
import com.example.purchase.master.service.VendorService;
import com.example.purchase.request.domain.RequestStatus;
import com.example.purchase.request.repository.RequestSearchCond;
import com.example.purchase.request.service.RequestDetail;
import com.example.purchase.request.service.RequestRow;
import com.example.purchase.request.service.RequestService;
import com.example.purchase.security.LoginUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 구매요청 (SCR-101 목록, SCR-102 작성·수정, SCR-103 상세) */
@Controller
@RequiredArgsConstructor
@RequestMapping("/requests")
public class RequestController {

    private final RequestService requestService;
    private final ItemService itemService;
    private final VendorService vendorService;
    private final CodeService codeService;

    /** 내 요청 목록 (REQ-06) */
    @GetMapping
    public String list(@ModelAttribute("cond") RequestSearchCond cond, @PageableDefault(size = 10) Pageable pageable,
                       @AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("page", requestService.searchMine(user, cond, pageable));
        model.addAttribute("statuses", RequestStatus.values());
        return "requests/list";
    }

    /** 검색 결과 엑셀 (REQ-08) */
    @GetMapping("/excel")
    public void excel(@ModelAttribute("cond") RequestSearchCond cond, @AuthenticationPrincipal LoginUser user,
                      HttpServletResponse response) throws IOException {
        List<RequestRow> rows = requestService.searchMineAll(user, cond);
        String fileName = "내구매요청_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + ".xlsx";
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString());
        ExcelWriter.write("구매요청", EXCEL_COLUMNS, rows, response.getOutputStream());
    }

    private static final List<ExcelColumn<RequestRow>> EXCEL_COLUMNS = List.of(
            new ExcelColumn<>("문서번호", RequestRow::requestNo, 8),
            new ExcelColumn<>("제목", RequestRow::title, 20),
            new ExcelColumn<>("거래처", RequestRow::vendorName, 10),
            new ExcelColumn<>("품목", RequestRow::itemSummary, 12),
            new ExcelColumn<>("합계(원)", RequestRow::totalAmount, 7),
            new ExcelColumn<>("상태", r -> r.status().getLabel(), 5),
            new ExcelColumn<>("작성일", RequestRow::createdAt, 9));

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", RequestForm.empty());
        return formView(model, null);
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") RequestForm form, BindingResult bindingResult,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            return formView(model, null);
        }
        Long id;
        try {
            id = requestService.create(form, files, user);
        } catch (BusinessException e) {
            bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            return formView(model, null);
        }
        redirect.addFlashAttribute("toast", "임시저장했습니다. 상신은 6단계에서 붙습니다.");
        return "redirect:/requests/" + id;
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, Model model) {
        model.addAttribute("r", requestService.getDetail(id, user));
        return "requests/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, Model model) {
        RequestDetail detail = requestService.getForEdit(id, user);
        model.addAttribute("form", RequestForm.from(detail));
        model.addAttribute("attachments", detail.attachments());
        return formView(model, id);
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("form") RequestForm form, BindingResult bindingResult,
                         @RequestParam(name = "files", required = false) List<MultipartFile> files,
                         @AuthenticationPrincipal LoginUser user, Model model, RedirectAttributes redirect) {
        if (!bindingResult.hasErrors()) {
            try {
                requestService.update(id, form, files, user);
                redirect.addFlashAttribute("toast", "저장했습니다.");
                return "redirect:/requests/" + id;
            } catch (BusinessException e) {
                bindingResult.reject(e.getErrorCode().getCode(), e.getMessage());
            }
        }
        model.addAttribute("attachments", requestService.getForEdit(id, user).attachments());
        return formView(model, id);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal LoginUser user, RedirectAttributes redirect) {
        requestService.delete(id, user);
        redirect.addFlashAttribute("toast", "요청서를 삭제했습니다.");
        return "redirect:/requests";
    }

    /** 첨부 내려받기. 한글 파일명은 UTF-8로 인코딩 */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long attachmentId,
                                             @AuthenticationPrincipal LoginUser user) {
        RequestService.Download d = requestService.download(id, attachmentId, user);
        MediaType type = d.contentType() != null ? MediaType.parseMediaType(d.contentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }

    private String formView(Model model, Long id) {
        model.addAttribute("id", id);
        model.addAttribute("items", itemService.selectableItems());
        model.addAttribute("vendors", vendorService.selectableVendors());
        model.addAttribute("purposes", codeService.activeCodes("PURCHASE_PURPOSE"));
        model.addAttribute("units", codeService.codeNames("UNIT"));
        if (!model.containsAttribute("attachments")) {
            model.addAttribute("attachments", List.of());
        }
        return "requests/form";
    }
}
