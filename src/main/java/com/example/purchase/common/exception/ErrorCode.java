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
    DUPLICATE_CODE(HttpStatus.CONFLICT, "D012", "이 그룹에 이미 있는 코드입니다."),

    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "I001", "품목을 찾을 수 없습니다."),
    DUPLICATE_ITEM_CODE(HttpStatus.CONFLICT, "I002", "이미 있는 품목 코드입니다."),
    VENDOR_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "거래처를 찾을 수 없습니다."),
    DUPLICATE_BIZ_NO(HttpStatus.CONFLICT, "V002", "이미 등록된 사업자등록번호입니다."),
    VENDOR_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "V003", "휴업·폐업했거나 사용 중지된 거래처는 고를 수 없습니다."),

    REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "구매요청서를 찾을 수 없습니다."),
    REQUEST_NOT_EDITABLE(HttpStatus.CONFLICT, "R002", "임시저장 상태인 요청서만 고치거나 지울 수 있습니다."),
    REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "R003", "이 구매요청서를 볼 권한이 없습니다."),
    TOO_MANY_ATTACHMENTS(HttpStatus.BAD_REQUEST, "R004", "첨부파일은 5개까지 올릴 수 있습니다."),
    ATTACHMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "R005", "첨부파일을 찾을 수 없습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "R006", "파일은 한 개에 10MB, 한 번에 50MB까지 올릴 수 있습니다."),
    FILE_STORE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "R007", "파일을 저장하지 못했습니다. 잠시 후 다시 시도해 주세요."),

    APPROVER_NOT_FOUND(HttpStatus.BAD_REQUEST, "A001", "결재자를 찾을 수 없어 상신할 수 없습니다. 관리자에게 조직도를 확인해 달라고 요청해 주세요."),
    NOT_YOUR_TURN(HttpStatus.FORBIDDEN, "A002", "지금 결재할 차례가 아닙니다."),
    ALREADY_PROCESSED(HttpStatus.CONFLICT, "A003", "다른 사용자가 먼저 처리했습니다. 화면을 새로 고쳐 확인해 주세요."),
    CANNOT_WITHDRAW(HttpStatus.CONFLICT, "A004", "첫 결재자가 처리하기 전에만 회수할 수 있습니다."),
    REJECT_COMMENT_REQUIRED(HttpStatus.BAD_REQUEST, "A005", "반려할 때는 의견을 꼭 적어 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
