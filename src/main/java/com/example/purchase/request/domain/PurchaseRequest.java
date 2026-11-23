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
import java.util.Optional;

/**
 * 구매요청서 (애그리거트 루트). 품목 줄과 첨부파일은 이 객체를 통해서만 바뀐다.
 * - 금액은 서버에서 다시 계산한다 (BR-07). 화면에서 온 합계는 쓰지 않는다.
 * - 임시저장(DRAFT)일 때만 고칠 수 있다 (BR-05).
 * - 결재선(approvalLines)도 이 객체의 메서드(submit/withdraw/approve/reject)로만 바뀐다.
 * - version: 낙관적 락. 결재 행위는 결재선(자식)만 바뀌는 경우가 있어서, 결재할 때는
 *   OPTIMISTIC_FORCE_INCREMENT로 읽어 이 version을 강제로 올린다 (docs/troubleshooting/04).
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
    // MySQL enum(...)이나 CHECK 없이 그냥 varchar: 상수를 추가해도 DB를 안 바꿔도 된다 (troubleshooting 09)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20)")
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

    /** 몇 번째 상신인지. 회수 후 다시 상신하면 1 → 2 */
    @Column(nullable = false)
    private int submitRound = 0;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("round asc, stepOrder asc")
    private List<ApprovalLine> approvalLines = new ArrayList<>();

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

    // ---------------- 결재 (BR-01 ~ BR-04) ----------------

    /**
     * 상신. 결재선을 만들고 첫 단계를 결재 차례(PENDING)로 둔다.
     * @param approvers 조직도에서 찾은 결재자 (ApprovalLineResolver가 순서대로 만들어 준다)
     */
    public void submit(List<Approver> approvers, LocalDateTime now) {
        checkEditable();
        if (approvers.isEmpty()) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND, "결재할 사람이 없어 상신할 수 없습니다.");
        }
        submitRound++;
        int step = 1;
        for (Approver a : approvers) {
            approvalLines.add(new ApprovalLine(this, submitRound, step, a.member(), a.stepName(),
                    step == 1 ? LineStatus.PENDING : LineStatus.WAITING, now));
            step++;
        }
        status = RequestStatus.IN_APPROVAL;
        submittedAt = now;
        completedAt = null;
    }

    /** 회수 (BR-04): 기안자 본인, 첫 결재자가 아직 처리하기 전에만 */
    public void withdraw(Long memberId) {
        if (!canWithdraw(memberId)) {
            throw new BusinessException(ErrorCode.CANNOT_WITHDRAW);
        }
        currentLines().forEach(ApprovalLine::cancel);
        status = RequestStatus.DRAFT;
        submittedAt = null;
    }

    /** 승인 (APV-02). 다음 단계가 있으면 넘기고, 마지막이면 문서를 승인으로 */
    public void approve(Long memberId, String comment, LocalDateTime now) {
        ApprovalLine line = pendingLineOf(memberId);
        line.approve(comment, now);
        Optional<ApprovalLine> next = currentLines().stream()
                .filter(l -> l.getStatus() == LineStatus.WAITING).findFirst();
        if (next.isPresent()) {
            next.get().makePending(now);
        } else {
            status = RequestStatus.APPROVED;
            completedAt = now;
        }
    }

    /** 반려 (APV-03, BR-03). 의견 필수, 남은 단계는 취소 */
    public void reject(Long memberId, String comment, LocalDateTime now) {
        if (comment == null || comment.isBlank()) {
            throw new BusinessException(ErrorCode.REJECT_COMMENT_REQUIRED);
        }
        ApprovalLine line = pendingLineOf(memberId);
        line.reject(comment, now);
        currentLines().stream().filter(l -> l.getStatus() == LineStatus.WAITING).forEach(ApprovalLine::cancel);
        status = RequestStatus.REJECTED;
        completedAt = now;
    }

    // ---------------- 발주·입고 (7단계) ----------------

    /** 발주 등록: 최종 승인된 문서만 (APPROVED → ORDERED) */
    public void markOrdered() {
        if (status == RequestStatus.ORDERED || status == RequestStatus.RECEIVED) {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }
        if (status != RequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_ALLOWED);
        }
        status = RequestStatus.ORDERED;
    }

    /** 입고 완료 (ORDERED → RECEIVED) */
    public void markReceived() {
        if (status != RequestStatus.ORDERED) {
            throw new BusinessException(ErrorCode.ALREADY_RECEIVED);
        }
        status = RequestStatus.RECEIVED;
    }

    /** 이번 회차 결재선 (회수 전 회차는 이력으로만 남는다) */
    public List<ApprovalLine> currentLines() {
        return approvalLines.stream().filter(l -> l.getRound() == submitRound).toList();
    }

    public Optional<ApprovalLine> pendingLine() {
        if (status != RequestStatus.IN_APPROVAL) {
            return Optional.empty();
        }
        return currentLines().stream().filter(l -> l.getStatus() == LineStatus.PENDING).findFirst();
    }

    public boolean isPendingApprover(Long memberId) {
        return pendingLine().map(l -> l.isApprover(memberId)).orElse(false);
    }

    public boolean canWithdraw(Long memberId) {
        if (status != RequestStatus.IN_APPROVAL || !isRequestedBy(memberId)) {
            return false;
        }
        List<ApprovalLine> lines = currentLines();
        return !lines.isEmpty() && lines.get(0).getStatus() == LineStatus.PENDING;
    }

    /** 이 문서의 결재선(어느 회차든)에 들어 있는 사람인지. 상세 화면 조회 권한에 쓴다 */
    public boolean hasApprover(Long memberId) {
        return approvalLines.stream().anyMatch(l -> l.isApprover(memberId));
    }

    private ApprovalLine pendingLineOf(Long memberId) {
        ApprovalLine line = pendingLine().orElseThrow(() -> new BusinessException(ErrorCode.ALREADY_PROCESSED));
        if (!line.isApprover(memberId)) {
            // 이미 내 단계를 처리했는데 또 누른 경우(두 탭, 뒤로 가기 후 다시 클릭)는 "차례 아님"보다 "이미 처리됨"이 맞는 안내
            boolean alreadyDone = currentLines().stream()
                    .anyMatch(l -> l.isApprover(memberId) && l.getStatus() == LineStatus.APPROVED);
            throw new BusinessException(alreadyDone ? ErrorCode.ALREADY_PROCESSED : ErrorCode.NOT_YOUR_TURN);
        }
        return line;
    }

    /** 결재선 한 단계에 들어갈 사람 */
    public record Approver(Member member, String stepName) {
    }

    /** 품목 한 줄 입력값 */
    public record Line(Item item, int quantity, BigDecimal unitPrice) {
    }
}
