package com.example.purchase.request.service;

import com.example.purchase.code.service.CodeService;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.common.file.FileStorage;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.service.ItemService;
import com.example.purchase.master.service.VendorService;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.service.MemberService;
import com.example.purchase.request.domain.Attachment;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.repository.PurchaseRequestRepository;
import com.example.purchase.request.repository.RequestSearchCond;
import com.example.purchase.request.web.RequestForm;
import com.example.purchase.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 구매요청서 작성·조회 (REQ-01~03, 06, 07).
 * 화면에는 엔티티 대신 RequestRow / RequestDetail을 돌려준다 (open-in-view: false).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RequestService {

    private final PurchaseRequestRepository requestRepository;
    private final MemberService memberService;
    private final ItemService itemService;
    private final VendorService vendorService;
    private final CodeService codeService;
    private final FileStorage fileStorage;

    // ---------------- 조회 ----------------

    public Page<RequestRow> searchMine(LoginUser user, RequestSearchCond cond, Pageable pageable) {
        return requestRepository.searchMine(user.getId(), cond, pageable).map(RequestRow::from);
    }

    public List<RequestRow> searchMineAll(LoginUser user, RequestSearchCond cond) {
        return requestRepository.searchMineAll(user.getId(), cond).stream().map(RequestRow::from).toList();
    }

    public RequestDetail getDetail(Long id, LoginUser user) {
        PurchaseRequest request = findDetail(id);
        checkCanView(request, user);
        return toDetail(request, user);
    }

    /** 수정 화면: 기안자 본인 + 임시저장일 때만 */
    public RequestDetail getForEdit(Long id, LoginUser user) {
        PurchaseRequest request = findDetail(id);
        checkOwner(request, user);
        request.checkEditable();
        return toDetail(request, user);
    }

    /** 첨부 내려받기: 요청서를 볼 수 있는 사람만 */
    public Download download(Long requestId, Long attachmentId, LoginUser user) {
        PurchaseRequest request = findDetail(requestId);
        checkCanView(request, user);
        Attachment a = request.findAttachment(attachmentId);
        return new Download(a.getOriginalName(), a.getContentType(), fileStorage.load(a.getStoredName()));
    }

    public record Download(String fileName, String contentType, Resource resource) {
    }

    // ---------------- 변경 ----------------

    @Transactional
    public Long create(RequestForm form, List<MultipartFile> files, LoginUser user) {
        Member requester = memberService.get(user.getId());
        Vendor vendor = vendorService.getSelectable(form.getVendorId());

        PurchaseRequest request = PurchaseRequest.draft(requester, form.getTitle(), form.getPurposeCode(), form.getReason(), vendor);
        request.replaceItems(toLines(form));
        addFiles(request, files);

        requestRepository.save(request);           // insert → id 생성
        request.assignRequestNo(LocalDateTime.now()); // 변경 감지로 문서번호 update
        return request.getId();
    }

    @Transactional
    public void update(Long id, RequestForm form, List<MultipartFile> files, LoginUser user) {
        PurchaseRequest request = findDetail(id);
        checkOwner(request, user);

        // 거래처를 바꿨을 때만 "고를 수 있는지" 다시 본다 (그사이 휴업이 됐어도 기존 거래처 유지는 허용)
        Vendor vendor = request.getVendor().getId().equals(form.getVendorId())
                ? request.getVendor() : vendorService.getSelectable(form.getVendorId());
        request.update(form.getTitle(), form.getPurposeCode(), form.getReason(), vendor);
        request.replaceItems(toLines(form));

        for (Long attachmentId : form.getDeleteAttachmentIds()) {
            fileStorage.deleteAfterCommit(request.removeAttachment(attachmentId)); // 파일은 커밋 뒤에 지움
        }
        addFiles(request, files);
    }

    @Transactional
    public void delete(Long id, LoginUser user) {
        PurchaseRequest request = findDetail(id);
        checkOwner(request, user);
        request.checkEditable();
        request.getAttachments().forEach(a -> fileStorage.deleteAfterCommit(a.getStoredName()));
        requestRepository.delete(request);
    }

    // ---------------- 내부 ----------------

    private PurchaseRequest findDetail(Long id) {
        return requestRepository.findDetail(id).orElseThrow(() -> new BusinessException(ErrorCode.REQUEST_NOT_FOUND));
    }

    /** 화면 단가가 아니라 폼의 단가를 쓰되, 금액·합계는 도메인이 다시 계산한다 */
    private List<PurchaseRequest.Line> toLines(RequestForm form) {
        return form.getItems().stream()
                .map(i -> new PurchaseRequest.Line(itemService.get(i.getItemId()), i.getQuantity(), i.getUnitPrice()))
                .toList();
    }

    private void addFiles(PurchaseRequest request, List<MultipartFile> files) {
        if (files == null) {
            return;
        }
        List<MultipartFile> real = files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (request.getAttachments().size() + real.size() > PurchaseRequest.MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorCode.TOO_MANY_ATTACHMENTS); // 파일을 쓰기 전에 먼저 거른다
        }
        for (MultipartFile file : real) {
            FileStorage.StoredFile stored = fileStorage.store(file); // 롤백되면 FileStorage가 지운다
            request.addAttachment(stored.originalName(), stored.storedName(), stored.size(), stored.contentType());
        }
    }

    private RequestDetail toDetail(PurchaseRequest request, LoginUser user) {
        Map<String, String> purposes = codeService.codeNames("PURCHASE_PURPOSE");
        Map<String, String> units = codeService.codeNames("UNIT");
        return RequestDetail.of(request, request.isRequestedBy(user.getId()), purposes, units);
    }

    /** 기안자, 구매팀, 관리자. (6단계에서 결재선에 있는 결재자도 추가) */
    private void checkCanView(PurchaseRequest request, LoginUser user) {
        boolean allowed = request.isRequestedBy(user.getId())
                || user.getRole() == Role.PURCHASING
                || user.getRole() == Role.ADMIN;
        if (!allowed) {
            throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
        }
    }

    private void checkOwner(PurchaseRequest request, LoginUser user) {
        if (!request.isRequestedBy(user.getId())) {
            throw new BusinessException(ErrorCode.REQUEST_ACCESS_DENIED);
        }
    }
}
