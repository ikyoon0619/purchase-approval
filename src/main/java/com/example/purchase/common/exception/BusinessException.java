package com.example.purchase.common.exception;

import lombok.Getter;

/** 업무 규칙 위반. 예상한 실패라서 에러 로그가 아니라 사용자 안내 메시지로 처리한다. */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}
