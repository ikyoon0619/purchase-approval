package com.example.purchase.code.service;

import com.example.purchase.code.domain.CodeGroup;
import com.example.purchase.code.domain.CommonCode;

import java.util.List;

/** 공통코드 목록 화면 한 줄. 트랜잭션 안에서 엔티티를 이 값으로 바꿔 화면에 넘긴다 */
public record CodeGroupRow(String groupCode, String name, boolean used, List<String> codeNames) {

    public static CodeGroupRow from(CodeGroup group) {
        return new CodeGroupRow(
                group.getGroupCode(),
                group.getName(),
                group.isUsed(),
                group.getCodes().stream().map(CommonCode::getName).toList()); // ← 여기서 코드 컬렉션을 읽는다
    }
}
