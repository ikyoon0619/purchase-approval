package com.example.purchase.request;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Item;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Position;
import com.example.purchase.org.domain.Role;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.PurchaseRequestItem;
import com.example.purchase.request.domain.RequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 도메인 규칙만 보는 단위 테스트 (스프링·DB 없이 빠르게) */
class PurchaseRequestTest {

    Department team = Department.team("D210", "개발팀", Department.division("D200", "사업본부"));
    Member requester = new Member("dev1", "pw", "김개발", null, team, new Position("사원", 1), Role.EMPLOYEE);
    Vendor vendor = new Vendor("1018100001", "(주)오피스몰", "김오피");
    Item laptop = new Item("IT-0001", "노트북", "IT", "EA", new BigDecimal("1650000"));
    Item paper = new Item("OF-0001", "복사용지", "OFFICE", "BOX", new BigDecimal("27000"));

    @Test
    @DisplayName("금액 = 수량 × 단가, 합계 = 금액의 합 (BR-07). 줄 번호는 1부터")
    void calculatesAmounts() {
        PurchaseRequest r = PurchaseRequest.draft(requester, "장비", "WORK", null, vendor);
        r.replaceItems(List.of(
                new PurchaseRequest.Line(laptop, 2, new BigDecimal("1600000")),   // 견적가로 단가 조정
                new PurchaseRequest.Line(paper, 3, new BigDecimal("27000"))));

        assertThat(r.getItems()).extracting(PurchaseRequestItem::getLineNo).containsExactly(1, 2);
        assertThat(r.getItems()).extracting(PurchaseRequestItem::getAmount)
                .containsExactly(new BigDecimal("3200000"), new BigDecimal("81000"));
        assertThat(r.getTotalAmount()).isEqualByComparingTo("3281000");
        assertThat(r.getStatus()).isEqualTo(RequestStatus.DRAFT);
        assertThat(r.getDepartment()).isSameAs(team); // 기안 당시 부서를 복사해 둔다
    }

    @Test
    @DisplayName("품목 줄을 다시 넣으면 기존 줄은 사라지고 합계도 새로 계산된다")
    void replaceItems() {
        PurchaseRequest r = PurchaseRequest.draft(requester, "장비", "WORK", null, vendor);
        r.replaceItems(List.of(new PurchaseRequest.Line(laptop, 1, laptop.getStandardPrice())));
        r.replaceItems(List.of(new PurchaseRequest.Line(paper, 2, paper.getStandardPrice())));

        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getTotalAmount()).isEqualByComparingTo("54000");
    }

    @Test
    @DisplayName("임시저장이 아니면 고칠 수 없다 (BR-05)")
    void onlyDraftEditable() {
        PurchaseRequest r = PurchaseRequest.draft(requester, "장비", "WORK", null, vendor);
        ReflectionTestUtils.setField(r, "status", RequestStatus.IN_APPROVAL); // 6단계 전이라 상태를 직접 바꿈

        assertThatThrownBy(() -> r.update("새 제목", "WORK", null, vendor))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REQUEST_NOT_EDITABLE);
        assertThatThrownBy(() -> r.replaceItems(List.of(new PurchaseRequest.Line(paper, 1, BigDecimal.ONE))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("품목은 1~20줄, 첨부는 5개까지")
    void limits() {
        PurchaseRequest r = PurchaseRequest.draft(requester, "장비", "WORK", null, vendor);
        assertThatThrownBy(() -> r.replaceItems(List.of())).isInstanceOf(BusinessException.class);

        for (int i = 0; i < 5; i++) {
            r.addAttachment("f" + i + ".pdf", "s" + i, 10, "application/pdf");
        }
        assertThatThrownBy(() -> r.addAttachment("f6.pdf", "s6", 10, "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_ATTACHMENTS);
    }

    @Test
    @DisplayName("문서번호: PR-연월-id 4자리")
    void requestNo() {
        PurchaseRequest r = PurchaseRequest.draft(requester, "장비", "WORK", null, vendor);
        ReflectionTestUtils.setField(r, "id", 7L);
        r.assignRequestNo(LocalDateTime.of(2026, 11, 23, 10, 0));
        assertThat(r.getRequestNo()).isEqualTo("PR-202611-0007");
    }
}
