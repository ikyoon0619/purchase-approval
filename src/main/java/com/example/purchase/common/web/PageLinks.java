package com.example.purchase.common.web;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * 페이지 번호 링크를 만든다. 지금 요청의 검색 조건(keyword 등)은 그대로 두고 page 값만 바꾼다.
 * 화면에서 ${@pageLinks.url(3)} 처럼 빈 이름으로 부른다.
 */
@Component("pageLinks")
public class PageLinks {

    /** @param page 1부터 시작하는 페이지 번호 (one-indexed-parameters: true) */
    public String url(int page) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replaceQueryParam("page", page)
                .build()
                .toUriString();
    }

    /** 같은 검색 조건으로 다른 주소(예: 엑셀 다운로드)를 만든다. page/size는 뺀다 */
    public String withSameQuery(String path) {
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath(path)
                .replaceQueryParam("page")
                .replaceQueryParam("size")
                .build()
                .toUriString();
    }
}
