package com.example.purchase.security;

import com.example.purchase.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {

    /** 로그인 화면. 로그인 처리(POST /login)는 Spring Security 필터가 한다 */
    @GetMapping("/login")
    public String login() {
        return "login";
    }

    /** 권한 없는 화면에 들어왔을 때 (403) */
    @GetMapping("/access-denied")
    public String accessDenied(HttpServletResponse response, Model model) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("code", ErrorCode.ACCESS_DENIED.getCode());
        model.addAttribute("message", ErrorCode.ACCESS_DENIED.getMessage());
        return "error/error";
    }
}
