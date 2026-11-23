package com.example.purchase.request.repository;

import com.example.purchase.request.domain.RequestStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 내 요청 목록 검색 조건 (REQ-06). 기간은 작성일 기준, 비어 있는 값은 조건에서 빠진다 */
public record RequestSearchCond(
        String keyword,
        RequestStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

    public static RequestSearchCond empty() {
        return new RequestSearchCond(null, null, null, null);
    }
}
