package com.example.purchase.code.service;

import com.example.purchase.code.domain.CommonCode;

/** select 박스에 넘기는 코드 값 */
public record CodeItem(String code, String name) {

    public static CodeItem from(CommonCode c) {
        return new CodeItem(c.getCode(), c.getName());
    }
}
