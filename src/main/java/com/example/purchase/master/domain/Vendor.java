package com.example.purchase.master.domain;

import com.example.purchase.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 거래처 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Vendor extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사업자등록번호 숫자 10자리 (하이픈 없이 저장, 화면에서 123-45-67890으로 보여 줌) */
    @Column(nullable = false, unique = true, length = 10)
    private String bizNo;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 30)
    private String ceoName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VendorStatus status = VendorStatus.UNKNOWN;

    private LocalDateTime statusCheckedAt;

    @Column(name = "use_yn", nullable = false)
    private boolean used = true;

    public Vendor(String bizNo, String name, String ceoName) {
        this.bizNo = bizNo;
        this.name = name;
        this.ceoName = ceoName;
    }

    public void update(String name, String ceoName, boolean used) {
        this.name = name;
        this.ceoName = ceoName;
        this.used = used;
    }

    /** 국세청 확인 결과(VendorStatusService) 또는 담당자가 직접 바꾼다. 확인 안 됨이면 checkedAt도 남겨 '언제 시도했는지' 보이게 */
    public void changeStatus(VendorStatus status, LocalDateTime checkedAt) {
        this.status = status;
        this.statusCheckedAt = checkedAt;
    }

    public boolean isSelectable() {
        return used && status.selectable();
    }

    public String getBizNoFormatted() {
        return bizNo.substring(0, 3) + "-" + bizNo.substring(3, 5) + "-" + bizNo.substring(5);
    }
}
