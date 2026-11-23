package com.example.purchase.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 화면(Thymeleaf) 요청에서 터진 예외를 한곳에서 받아 error/error.html로 보여 준다.
 * - BusinessException: 예상한 실패 → 메시지만 보여 줌 (WARN 로그)
 * - 그 밖의 예외: 예상 못 한 실패 → 일반 메시지 + ERROR 로그 (10단계에서 DB에도 저장)
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public String handleBusiness(BusinessException e, HttpServletRequest request,
                                 HttpServletResponse response, Model model) {
        log.warn("[{}] {} {}", e.getErrorCode().getCode(), request.getRequestURI(), e.getMessage());
        response.setStatus(e.getErrorCode().getStatus().value());
        model.addAttribute("code", e.getErrorCode().getCode());
        model.addAttribute("message", e.getMessage());
        return "error/error";
    }

    /** 첨부파일이 max-file-size(10MB) / max-request-size(50MB)를 넘으면 컨트롤러에 오기 전에 터진다 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleTooLarge(HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("[{}] {} 업로드 크기 초과", ErrorCode.FILE_TOO_LARGE.getCode(), request.getRequestURI());
        response.setStatus(ErrorCode.FILE_TOO_LARGE.getStatus().value());
        model.addAttribute("code", ErrorCode.FILE_TOO_LARGE.getCode());
        model.addAttribute("message", ErrorCode.FILE_TOO_LARGE.getMessage());
        return "error/error";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public String handleNotFound(HttpServletResponse response, Model model) {
        response.setStatus(404);
        model.addAttribute("code", "404");
        model.addAttribute("message", "페이지를 찾을 수 없습니다.");
        return "error/error";
    }

    @ExceptionHandler(Exception.class)
    public String handleUnexpected(Exception e, HttpServletRequest request,
                                   HttpServletResponse response, Model model) {
        log.error("처리하지 못한 예외: {}", request.getRequestURI(), e);
        response.setStatus(500);
        model.addAttribute("code", ErrorCode.INTERNAL_ERROR.getCode());
        model.addAttribute("message", ErrorCode.INTERNAL_ERROR.getMessage());
        return "error/error";
    }
}
