package com.example.purchase.stats.web;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Role;
import com.example.purchase.security.LoginUser;
import com.example.purchase.stats.batch.BatchRunner;
import com.example.purchase.stats.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Year;
import java.time.YearMonth;
import java.util.stream.IntStream;

/** 월별 구매 통계 (SCR-603). 보기: 메뉴 권한(부서장·구매팀·관리자). 다시 집계: 구매팀·관리자만 */
@Controller
@RequestMapping("/stats/monthly")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final BatchRunner batchRunner;

    @GetMapping
    public String monthly(@RequestParam(required = false) Integer year, @AuthenticationPrincipal LoginUser user, Model model) {
        int y = year == null ? Year.now().getValue() : year;
        model.addAttribute("table", statsService.yearTable(y));
        model.addAttribute("years", IntStream.rangeClosed(Year.now().getValue() - 2, Year.now().getValue()).boxed().toList());
        model.addAttribute("months", IntStream.rangeClosed(1, 12).boxed().toList());
        model.addAttribute("lastAggregatedAt", statsService.lastAggregatedAt().orElse(null));
        model.addAttribute("canRerun", canRerun(user));
        model.addAttribute("defaultMonth", YearMonth.now().toString());
        return "stats/monthly";
    }

    /** 한 달 다시 집계. 기다렸다가(동기) 결과를 보여 준다. 부서 수만큼만 쓰므로 금방 끝난다 */
    @PostMapping("/rerun")
    public String rerun(@RequestParam String month, @AuthenticationPrincipal LoginUser user, RedirectAttributes ra) {
        if (!canRerun(user)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "다시 집계는 구매팀과 관리자만 할 수 있습니다.");
        }
        YearMonth ym = YearMonth.parse(month);
        try {
            JobExecution e = batchRunner.runMonthlyStat(ym);
            if (e.getStatus() == BatchStatus.COMPLETED) {
                long written = e.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
                ra.addFlashAttribute("toast", "%s 통계를 다시 집계했습니다 (부서 %d곳).".formatted(month, written));
            } else {
                ra.addFlashAttribute("error", "%s 집계가 %s 상태로 끝났습니다. 서버 로그를 확인해 주세요.".formatted(month, e.getStatus()));
            }
        } catch (BusinessException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/stats/monthly?year=" + ym.getYear();
    }

    private boolean canRerun(LoginUser user) {
        return user.getRole() == Role.PURCHASING || user.getRole() == Role.ADMIN;
    }
}
