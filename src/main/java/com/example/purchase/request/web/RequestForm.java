package com.example.purchase.request.web;

import com.example.purchase.request.service.RequestDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** 구매요청서 작성·수정 입력 (REQ-01). 첨부파일은 MultipartFile로 따로 받는다 */
@Getter
@Setter
@NoArgsConstructor
public class RequestForm {

    @NotBlank(message = "제목을 입력해 주세요.")
    @Size(max = 200, message = "제목은 200자까지입니다.")
    private String title;

    @NotBlank(message = "구매 목적을 골라 주세요.")
    private String purposeCode;

    @Size(max = 2000, message = "구매 사유는 2,000자까지입니다.")
    private String reason;

    @NotNull(message = "거래처를 골라 주세요.")
    private Long vendorId;

    /** @Valid: 리스트 안의 각 줄도 검증한다. items[0].quantity 같은 이름으로 오류가 붙는다 */
    @Valid
    @NotEmpty(message = "품목을 한 줄 이상 입력해 주세요.")
    @Size(max = 20, message = "품목은 20줄까지입니다.")
    private List<RequestItemForm> items = new ArrayList<>();

    /** 수정 화면에서 지우기로 체크한 첨부 id */
    private List<Long> deleteAttachmentIds = new ArrayList<>();

    public static RequestForm empty() {
        RequestForm f = new RequestForm();
        f.items.add(new RequestItemForm()); // 빈 줄 하나로 시작
        return f;
    }

    public static RequestForm from(RequestDetail d) {
        RequestForm f = new RequestForm();
        f.title = d.title();
        f.purposeCode = d.purposeCode();
        f.reason = d.reason();
        f.vendorId = d.vendorId();
        d.items().forEach(l -> f.items.add(new RequestItemForm(l.itemId(), l.quantity(), l.unitPrice())));
        return f;
    }

    /** 반려된 문서 복사. 제목 앞에 [재작성]을 붙인다 (200자 제한 안에서) */
    public static RequestForm copyOf(RequestDetail d) {
        RequestForm f = from(d);
        String title = "[재작성] " + d.title();
        f.title = title.length() > 200 ? title.substring(0, 200) : title;
        return f;
    }
}
