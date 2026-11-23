package com.example.purchase.master.web;

import com.example.purchase.master.domain.Vendor;
import com.example.purchase.master.domain.VendorStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VendorForm {

    /** 하이픈이 있어도 되고 없어도 된다. 저장할 때 숫자만 남긴다 */
    @NotBlank(message = "사업자등록번호를 입력해 주세요.")
    @Pattern(regexp = "^\\d{3}-?\\d{2}-?\\d{5}$", message = "사업자등록번호는 숫자 10자리입니다. 예) 123-45-67890")
    private String bizNo;

    @NotBlank(message = "상호를 입력해 주세요.")
    @Size(max = 100, message = "상호는 100자까지입니다.")
    private String name;

    @Size(max = 30, message = "대표자명은 30자까지입니다.")
    private String ceoName;

    @NotNull
    private VendorStatus status = VendorStatus.UNKNOWN;

    private boolean used = true;

    public String bizNoDigits() {
        return bizNo == null ? null : bizNo.replace("-", "");
    }

    public static VendorForm from(Vendor v) {
        VendorForm f = new VendorForm();
        f.bizNo = v.getBizNoFormatted();
        f.name = v.getName();
        f.ceoName = v.getCeoName();
        f.status = v.getStatus();
        f.used = v.isUsed();
        return f;
    }
}
