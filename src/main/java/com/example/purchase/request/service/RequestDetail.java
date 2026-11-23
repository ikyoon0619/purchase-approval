package com.example.purchase.request.service;

import com.example.purchase.request.domain.Attachment;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.PurchaseRequestItem;
import com.example.purchase.request.domain.RequestStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 요청서 상세 화면에 넘기는 값. 엔티티 대신 이걸 넘기는 이유:
 * open-in-view를 껐기 때문에 화면(Thymeleaf)이 그려질 때는 DB 세션이 이미 닫혀 있다.
 * 엔티티를 넘기면 화면에서 r.items[0].item.name 같은 지연 로딩이 터진다 (LazyInitializationException).
 * 그래서 트랜잭션 안에서 필요한 값을 전부 꺼내 담아 넘긴다 (docs/troubleshooting/03).
 */
public record RequestDetail(
        Long id, String requestNo, String title, String purposeCode, String purposeName, String reason,
        String requesterName, String departmentName, Long vendorId, String vendorName, String vendorBizNo,
        BigDecimal totalAmount, RequestStatus status, LocalDateTime createdAt, LocalDateTime updatedAt,
        boolean editable, List<Line> items, List<File> attachments) {

    public record Line(int lineNo, Long itemId, String itemCode, String itemName, String unitName,
                       int quantity, BigDecimal unitPrice, BigDecimal amount) {
    }

    public record File(Long id, String originalName, long size) {
        /** 1.2 MB, 350 KB 처럼 */
        public String sizeText() {
            return size >= 1024 * 1024 ? String.format("%.1f MB", size / 1024.0 / 1024.0) : Math.max(1, size / 1024) + " KB";
        }
    }

    static RequestDetail of(PurchaseRequest r, boolean editableByViewer, Map<String, String> purposeNames, Map<String, String> unitNames) {
        List<Line> lines = r.getItems().stream().map((PurchaseRequestItem i) -> new Line(
                i.getLineNo(), i.getItem().getId(), i.getItem().getCode(), i.getItem().getName(),
                unitNames.getOrDefault(i.getItem().getUnitCode(), i.getItem().getUnitCode()),
                i.getQuantity(), i.getUnitPrice(), i.getAmount())).toList();
        List<File> files = r.getAttachments().stream()
                .map((Attachment a) -> new File(a.getId(), a.getOriginalName(), a.getSize())).toList();
        return new RequestDetail(
                r.getId(), r.getRequestNo(), r.getTitle(), r.getPurposeCode(),
                purposeNames.getOrDefault(r.getPurposeCode(), r.getPurposeCode()), r.getReason(),
                r.getRequester().getName(), r.getDepartment().getName(),
                r.getVendor().getId(), r.getVendor().getName(), r.getVendor().getBizNoFormatted(),
                r.getTotalAmount(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt(),
                editableByViewer && r.isEditable(), lines, files);
    }
}
