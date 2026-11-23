package com.example.purchase.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 업무 오류를 코드로 모아 둔다. 화면 메시지와 HTTP 상태를 한곳에서 관리하기 위해서다.
 * 단계가 진행되면서 결재·재고 관련 코드가 늘어난다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값을 확인해 주세요."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C002", "요청한 데이터를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."),

    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "부서를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
