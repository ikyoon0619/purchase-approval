package com.example.purchase.audit;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 감사 로그에 남기는 행위 (OPS-01). 문자열로 저장한다 (@Enumerated(STRING)) */
@Getter
@RequiredArgsConstructor
public enum AuditAction {
    CREATE("등록"),
    UPDATE("수정"),
    DELETE("삭제"),
    SUBMIT("상신"),
    WITHDRAW("회수"),
    APPROVE("승인"),
    REJECT("반려"),
    ORDER("발주"),
    RECEIVE("입고"),
    SHIP("출고"),
    PERMISSION("권한 변경"),
    BATCH("배치 실행");

    private final String label;
}
