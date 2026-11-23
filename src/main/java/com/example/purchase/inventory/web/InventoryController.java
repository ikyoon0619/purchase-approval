package com.example.purchase.inventory.web;

import com.example.purchase.code.service.CodeService;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 재고 현황(SCR-303), 재고 이력·출고(SCR-304). 메뉴 권한: 구매팀, 관리자 */
@Controller
@RequiredArgsConstructor
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final CodeService codeService;

    @GetMapping
    public String list(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "false") boolean onlyInStock,
                       @PageableDefault(size = 20) Pageable pageable, Model model) {
        model.addAttribute("keyword", keyword);
        model.addAttribute("onlyInStock", onlyInStock);
        model.addAttribute("page", inventoryService.search(keyword, onlyInStock, pageable));
        model.addAttribute("categories", codeService.codeNames("ITEM_CATEGORY"));
        model.addAttribute("units", codeService.codeNames("UNIT"));
        return "inventory/list";
    }

    @GetMapping("/{itemId}/history")
    public String history(@PathVariable Long itemId, @PageableDefault(size = 20) Pageable pageable, Model model) {
        model.addAttribute("stock", inventoryService.get(itemId));
        model.addAttribute("page", inventoryService.history(itemId, pageable));
        model.addAttribute("units", codeService.codeNames("UNIT"));
        return "inventory/history";
    }

    /** 출고 (사용 부서로 내보냄). 재고보다 많으면 거절 */
    @PostMapping("/{itemId}/ship")
    public String ship(@PathVariable Long itemId, @RequestParam(required = false) Integer quantity,
                       @RequestParam(required = false) String memo, RedirectAttributes redirect) {
        try {
            if (quantity == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "출고 수량을 입력해 주세요.");
            }
            String cleanMemo = memo == null || memo.isBlank() ? null : memo.trim();
            if (cleanMemo != null && cleanMemo.length() > 200) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "메모는 200자까지입니다.");
            }
            int after = inventoryService.ship(itemId, quantity, cleanMemo);
            redirect.addFlashAttribute("toast", quantity + "개 출고했습니다. 남은 재고 " + after + "개");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (PessimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", ErrorCode.STOCK_BUSY.getMessage());
        }
        return "redirect:/inventory/" + itemId + "/history";
    }
}
