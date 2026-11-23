package com.example.purchase.home;

import com.example.purchase.org.repository.DepartmentRepository;
import com.example.purchase.org.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 첫 화면(대시보드). 지금은 DB 연결이 잘 되는지 보여 주는 용도이고,
 * 9단계에서 결재 대기 건수와 구매 통계 차트로 바뀐다.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DepartmentRepository departmentRepository;
    private final PositionRepository positionRepository;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("departments", departmentRepository.search(null, true));
        model.addAttribute("positions", positionRepository.findAllByOrderByRankOrderAsc());
        return "home";
    }
}
