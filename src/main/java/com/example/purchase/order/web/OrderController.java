package com.example.purchase.order.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.order.domain.OrderStatus;
import com.example.purchase.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/** 발주 목록(SCR-301), 발주 상세·입고(SCR-302). 메뉴 권한: 구매팀, 관리자 */
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    /** tab: waiting(발주 대기 = 최종 승인된 요청서) / ordered(입고 대기) / received(입고 완료) */
    @GetMapping
    public String list(@RequestParam(defaultValue = "waiting") String tab, @PageableDefault(size = 10) Pageable pageable,
                       Model model) {
        model.addAttribute("tab", tab);
        switch (tab) {
            case "ordered" -> model.addAttribute("page", orderService.search(OrderStatus.ORDERED, pageable));
            case "received" -> model.addAttribute("page", orderService.search(OrderStatus.RECEIVED, pageable));
            default -> {
                model.addAttribute("tab", "waiting");
                model.addAttribute("page", orderService.waiting(pageable));
            }
        }
        return "orders/list";
    }

    @PostMapping
    public String create(@RequestParam Long requestId, RedirectAttributes redirect) {
        try {
            Long id = orderService.create(requestId);
            redirect.addFlashAttribute("toast", "발주서를 만들었습니다. 물건이 들어오면 입고 처리해 주세요.");
            return "redirect:/orders/" + id;
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            // 두 사람이 같은 요청서로 동시에 발주: 요청서 version 충돌 또는 request_id UNIQUE 위반
            log.info("[{}] 요청서 {} 동시 발주: {}", ErrorCode.ALREADY_ORDERED.getCode(), requestId, e.getClass().getSimpleName());
            redirect.addFlashAttribute("error", ErrorCode.ALREADY_ORDERED.getMessage());
        }
        return "redirect:/orders";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("o", orderService.getDetail(id));
        return "orders/detail";
    }

    /** 입고 수량은 qty_{발주줄id} 이름으로 온다. 비어 있으면 발주 수량 그대로 */
    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id, @RequestParam Map<String, String> params, RedirectAttributes redirect) {
        try {
            orderService.receive(id, parseQuantities(params));
            redirect.addFlashAttribute("toast", "입고 처리했습니다. 재고에 반영됐습니다.");
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        } catch (PessimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", ErrorCode.STOCK_BUSY.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("error", ErrorCode.ALREADY_RECEIVED.getMessage());
        }
        return "redirect:/orders/" + id;
    }

    private static Map<Long, Integer> parseQuantities(Map<String, String> params) {
        Map<Long, Integer> result = new HashMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("qty_") && v != null && !v.isBlank()) {
                try {
                    result.put(Long.valueOf(k.substring(4)), Integer.valueOf(v.trim()));
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.INVALID_RECEIVE_QUANTITY);
                }
            }
        });
        return result;
    }
}
