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

    ACCESS_DENIED(HttpStatus.FORBIDDEN, "C003", "이 화면을 볼 권한이 없습니다."),

    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "O001", "부서를 찾을 수 없습니다."),
    DUPLICATE_DEPARTMENT_CODE(HttpStatus.CONFLICT, "O002", "이미 있는 부서 코드입니다."),
    INVALID_PARENT_DEPARTMENT(HttpStatus.BAD_REQUEST, "O003", "상위 부서는 본부만 지정할 수 있습니다."),
    POSITION_NOT_FOUND(HttpStatus.NOT_FOUND, "O011", "직급을 찾을 수 없습니다."),
    DUPLICATE_POSITION_NAME(HttpStatus.CONFLICT, "O012", "이미 있는 직급 이름입니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "O021", "사용자를 찾을 수 없습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "O022", "이미 쓰고 있는 아이디입니다."),

    MENU_NOT_FOUND(HttpStatus.NOT_FOUND, "M001", "메뉴를 찾을 수 없습니다."),

    CODE_GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "D001", "코드 그룹을 찾을 수 없습니다."),
    DUPLICATE_CODE_GROUP(HttpStatus.CONFLICT, "D002", "이미 있는 그룹 코드입니다."),
    CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "D011", "코드를 찾을 수 없습니다."),
    DUPLICATE_CODE(HttpStatus.CONFLICT, "D012", "이 그룹에 이미 있는 코드입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
