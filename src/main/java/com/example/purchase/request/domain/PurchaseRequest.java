package com.example.purchase.request.domain;

import com.example.purchase.common.entity.BaseEntity;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Item;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 구매요청서 (애그리거트 루트). 품목 줄과 첨부파일은 이 객체를 통해서만 바뀐다.
 * - 금액은 서버에서 다시 계산한다 (BR-07). 화면에서 온 합계는 쓰지 않는다.
 * - 임시저장(DRAFT)일 때만 고칠 수 있다 (BR-05).
 * - version: 6단계 결재 동시 처리(낙관적 락)에 쓴다. 미리 넣어 둔다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseRequest extends BaseEntity {

    public static final int MAX_ITEMS = 20;
    public static final int MAX_ATTACHMENTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 문서번호 PR-202611-0001. id가 생긴 뒤에 붙인다 */
    @Column(unique = true, length = 20)
    private String requestNo;

    @Column(nullable = false, length = 200)
    private String title;

    /** 공통코드 PURCHASE_PURPOSE */
    @Column(nullable = false, length = 30)
    private String purposeCode;

    @Column(length = 2000)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id")
    private Member requester;

    /** 기안 당시 부서. 나중에 기안자가 부서를 옮겨도 문서의 부서는 그대로 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vendor_id")
    private Vendor vendor;

    @Column(nullable = false, precision = 15, scale = 0)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestStatus status = RequestStatus.DRAFT;

    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo asc")
    private List<PurchaseRequestItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<Attachment> attachments = new ArrayList<>();

    private PurchaseRequest(Member requester, String title, String purposeCode, String reason, Vendor vendor) {
        this.requester = requester;
        this.department = requester.getDepartment();
        this.title = title;
        this.purposeCode = purposeCode;
        this.reason = reason;
        this.vendor = vendor;
    }

    /** 새 요청서는 항상 임시저장으로 시작한다 */
    public static PurchaseRequest draft(Member requester, String title, String purposeCode, String reason, Vendor vendor) {
        return new PurchaseRequest(requester, title, purposeCode, reason, vendor);
    }

    /** 저장해서 id가 생긴 뒤 호출. 예) id 7, 2026년 11월 → PR-202611-0007 */
    public void assignRequestNo(LocalDateTime now) {
        this.requestNo = "PR-" + now.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + String.format("%04d", id);
    }

    public void update(String title, String purposeCode, String reason, Vendor vendor) {
        checkEditable();
        this.title = title;
        this.purposeCode = purposeCode;
        this.reason = reason;
        this.vendor = vendor;
    }

    /** 품목 줄을 통째로 바꾸고 합계를 다시 계산한다. 빠진 줄은 orphanRemoval로 DB에서도 지워진다 */
    public void replaceItems(List<Line> lines) {
        checkEditable();
        if (lines.isEmpty() || lines.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "품목은 1줄 이상 " + MAX_ITEMS + "줄 이하로 입력해 주세요.");
        }
        items.clear();
        int lineNo = 1;
        for (Line line : lines) {
            items.add(new PurchaseRequestItem(this, lineNo++, line.item(), line.quantity(), line.unitPrice()));
        }
        totalAmount = items.stream().map(PurchaseRequestItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void addAttachment(String originalName, String storedName, long size, String contentType) {
        checkEditable();
        if (attachments.size() >= MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_ATTACHMENTS);
        }
        attachments.add(new Attachment(this, originalName, storedName, size, contentType));
    }

    /** @return 지운 첨부의 서버 저장 이름 (트랜잭션이 끝난 뒤 실제 파일을 지우려고 돌려준다) */
    public String removeAttachment(Long attachmentId) {
        checkEditable();
        Attachment target = findAttachment(attachmentId);
        attachments.remove(target);
        return target.getStoredName();
    }

    public Attachment findAttachment(Long attachmentId) {
        return attachments.stream().filter(a -> a.getId().equals(attachmentId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    public boolean isEditable() {
        return status == RequestStatus.DRAFT;
    }

    public void checkEditable() {
        if (!isEditable()) {
            throw new BusinessException(ErrorCode.REQUEST_NOT_EDITABLE);
        }
    }

    /** 프록시의 getId()는 DB를 읽지 않는다 (id는 프록시가 이미 알고 있음) */
    public boolean isRequestedBy(Long memberId) {
        return requester.getId().equals(memberId);
    }

    /** 품목 한 줄 입력값 */
    public record Line(Item item, int quantity, BigDecimal unitPrice) {
    }
}
