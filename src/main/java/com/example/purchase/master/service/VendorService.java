package com.example.purchase.master.service;

import com.example.purchase.audit.AuditAction;
import com.example.purchase.audit.Audited;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import com.example.purchase.master.repository.VendorRepository;
import com.example.purchase.master.web.VendorForm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static com.example.purchase.master.service.ItemService.blankToNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorService {

    private final VendorRepository vendorRepository;

    public Page<Vendor> search(String keyword, VendorStatus status, Pageable pageable) {
        String k = blankToNull(keyword);
        return vendorRepository.search(k == null ? null : k.replace("-", ""), status, pageable);
    }

    /** 요청서에서 고를 수 있는 거래처 (BR-09) */
    public List<Vendor> selectableVendors() {
        return vendorRepository.findByUsedTrueAndStatusInOrderByNameAsc(EnumSet.of(VendorStatus.ACTIVE, VendorStatus.UNKNOWN));
    }

    public Vendor get(Long id) {
        return vendorRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.VENDOR_NOT_FOUND));
    }

    /** 요청서에 쓸 거래처. 휴업·폐업·사용 중지면 거절 */
    public Vendor getSelectable(Long id) {
        Vendor vendor = get(id);
        if (!vendor.isSelectable()) {
            throw new BusinessException(ErrorCode.VENDOR_NOT_SELECTABLE);
        }
        return vendor;
    }

    @Transactional
    @Audited(action = AuditAction.CREATE, target = "거래처", id = "#result")
    public Long create(VendorForm form) {
        if (vendorRepository.existsByBizNo(form.bizNoDigits())) {
            throw new BusinessException(ErrorCode.DUPLICATE_BIZ_NO);
        }
        Vendor vendor = new Vendor(form.bizNoDigits(), form.getName(), form.getCeoName());
        vendor.changeStatus(form.getStatus(), form.getStatus() == VendorStatus.UNKNOWN ? null : LocalDateTime.now());
        return vendorRepository.save(vendor).getId();
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, target = "거래처", id = "#id")
    public void update(Long id, VendorForm form) {
        Vendor vendor = get(id);
        vendor.update(form.getName(), form.getCeoName(), form.isUsed());
        if (vendor.getStatus() != form.getStatus()) {
            vendor.changeStatus(form.getStatus(), LocalDateTime.now());
        }
    }

    /** 국세청 확인 결과 저장. 짧은 쓰기 트랜잭션 (API 호출은 이 바깥에서 끝난 뒤) */
    @Transactional
    public void applyStatus(Long id, VendorStatus status, LocalDateTime checkedAt) {
        get(id).changeStatus(status, checkedAt);
    }

    public List<Vendor> usedVendors() {
        return vendorRepository.findByUsedTrueOrderByIdAsc();
    }
}
