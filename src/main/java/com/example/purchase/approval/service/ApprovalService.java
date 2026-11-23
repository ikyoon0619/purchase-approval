package com.example.purchase.approval.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.approval.repository.ApprovalLineRepository;
import com.example.purchase.approval.repository.InboxTab;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.request.domain.LineStatus;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상신·회수·승인·반려 (REQ-04, REQ-05, APV-01~04).
 * 상태를 바꾸는 규칙은 PurchaseRequest 안에 있고, 여기서는 "누가 · 어떤 문서를" 정해서 넘기기만 한다.
 *
 * 처리할 문서는 항상 findForApproval(OPTIMISTIC_FORCE_INCREMENT)로 읽는다.
 * 그래서 두 사람이(또는 한 사람이 두 탭에서) 같은 문서를 동시에 처리하면 나중에 커밋하는 쪽이
 * ObjectOptimisticLockingFailureException으로 실패한다 → 컨트롤러가 "다른 사용자가 먼저 처리했습니다"로 안내 (BR-06).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ApprovalService {

    public static final int MAX_COMMENT = 500;

    private final PurchaseRequestRepository requestRepository;
    private final ApprovalLineRepository lineRepository;
    private final ApprovalLineResolver resolver;

    @Audited(action = AuditAction.SUBMIT, target = "구매요청서", id = "#requestId")
    public void submit(Long requestId, LoginUser user) {
        PurchaseRequest request = findForApproval(requestId);
        if (!request.isRequestedBy(user.getId())) {
            throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
        }
        request.checkEditable();
        request.submit(resolver.resolve(request), LocalDateTime.now());
    }

    @Audited(action = AuditAction.WITHDRAW, target = "구매요청서", id = "#requestId")
    public void withdraw(Long requestId, LoginUser user) {
        findForApproval(requestId).withdraw(user.getId());
    }

    @Audited(action = AuditAction.APPROVE, target = "구매요청서", id = "#requestId")
    public void approve(Long requestId, LoginUser user, String comment) {
        findForApproval(requestId).approve(user.getId(), clean(comment), LocalDateTime.now());
    }

    @Audited(action = AuditAction.REJECT, target = "구매요청서", id = "#requestId")
    public void reject(Long requestId, LoginUser user, String comment) {
        findForApproval(requestId).reject(user.getId(), clean(comment), LocalDateTime.now());
    }

    /**
     * 상신 전에 "이렇게 결재선이 만들어집니다"를 미리 보여 준다. 실패하면 이유를 담아 돌려준다.
     * 락이 필요 없으니 일반 상세 조회로 읽는다.
     */
    @Transactional(readOnly = true)
    public Preview preview(Long requestId) {
        PurchaseRequest request = requestRepository.findDetail(requestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
        try {
            List<Preview.Step> steps = resolver.resolve(request).stream()
                    .map(a -> new Preview.Step(a.stepName(), a.member().getName(), a.member().getPosition().getName()))
                    .toList();
            return new Preview(steps, null);
        } catch (BusinessException e) {
            return new Preview(List.of(), e.getMessage());
        }
    }

    public record Preview(List<Step> steps, String error) {
        public record Step(String stepName, String approverName, String positionName) {
        }
    }

    @Transactional(readOnly = true)
    public Page<InboxRow> inbox(LoginUser user, InboxTab tab, Pageable pageable) {
        return lineRepository.searchInbox(user.getId(), tab, pageable).map(InboxRow::from);
    }

    @Transactional(readOnly = true)
    public long pendingCount(Long memberId) {
        return lineRepository.countByApproverIdAndStatus(memberId, LineStatus.PENDING);
    }

    private PurchaseRequest findForApproval(Long id) {
        return requestRepository.findForApproval(id).orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
    }

    private static String clean(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > MAX_COMMENT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "의견은 " + MAX_COMMENT + "자까지 적을 수 있습니다.");
        }
        return trimmed;
    }
}
