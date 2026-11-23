package com.example.purchase.common.excel;

import java.util.function.Function;

/**
 * 엑셀 한 열의 정의: 머리글, 값을 꺼내는 방법, 열 너비(글자 수).
 * 예) new ExcelColumn<Member>("아이디", Member::getLoginId, 14)
 */
public record ExcelColumn<T>(String header, Function<T, Object> value, int width) {
}
