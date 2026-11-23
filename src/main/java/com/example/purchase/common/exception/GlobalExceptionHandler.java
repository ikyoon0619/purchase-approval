package com.example.purchase.common.exception;

import com.example.purchase.errorlog.service.ErrorLogService;
import com.example.purchase.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 화면(Thymeleaf) 요청에서 터진 예외를 한곳에서 받아 error/error.html로 보여 준다.
 * - BusinessException: 예상한 실패 → 메시지만 보여 줌 (WARN 로그)
 * - 그 밖의 예외: 예상 못 한 실패 → 일반 메시지 + ERROR 로그 + error_log 테이블 저장(10단계).
 *   사용자에게는 오류 번호만 보여 주고, 관리자가 그 번호로 스택을 찾는다.
 */
@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ErrorLogService errorLogService;

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

    /**
     * 주소가 잘못된 요청: /requests/abc (숫자 자리에 글자), 필수 파라미터 빠짐.
     * 처음엔 Exception 처리기로 떨어져서 500 + 에러 로그로 쌓였다. 사용자의 오타·봇의 이상한 주소가
     * "처리 못 한 서버 오류"로 섞이면 진짜 장애가 묻힌다 → 400으로 돌려주고 에러 로그에는 남기지 않는다.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public String handleBadRequest(Exception e, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.warn("[{}] {} 잘못된 요청: {}", ErrorCode.INVALID_INPUT.getCode(), request.getRequestURI(), e.getMessage());
        response.setStatus(400);
        model.addAttribute("code", ErrorCode.INVALID_INPUT.getCode());
        model.addAttribute("message", "주소나 입력값이 올바르지 않습니다.");
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
        Long errorId = errorLogService.record(request.getMethod() + " " + request.getRequestURI(), loginId(), e);
        log.error("처리하지 못한 예외 #{}: {}", errorId, request.getRequestURI(), e);
        response.setStatus(500);
        model.addAttribute("code", ErrorCode.INTERNAL_ERROR.getCode());
        model.addAttribute("message", ErrorCode.INTERNAL_ERROR.getMessage());
        model.addAttribute("errorId", errorId);
        return "error/error";
    }

    private String loginId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof LoginUser u ? u.getLoginId() : null;
    }
}
