package com.example.purchase.master.web;

import com.example.purchase.master.domain.Item;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ItemForm {

    @NotBlank(message = "품목 코드를 입력해 주세요.")
    @Pattern(regexp = "^[A-Z0-9-]{3,20}$", message = "품목 코드는 대문자, 숫자, - 로 3~20자입니다. 예) IT-0001")
    private String code;

    @NotBlank(message = "품목명을 입력해 주세요.")
    @Size(max = 100, message = "품목명은 100자까지입니다.")
    private String name;

    @NotBlank(message = "분류를 골라 주세요.")
    private String categoryCode;

    @NotBlank(message = "단위를 골라 주세요.")
    private String unitCode;

    @NotNull(message = "기준 단가를 입력해 주세요.")
    @DecimalMin(value = "0", message = "단가는 0원 이상입니다.")
    @Digits(integer = 15, fraction = 0, message = "단가는 원 단위 정수로 입력해 주세요.")
    private BigDecimal standardPrice;

    private boolean used = true;

    public static ItemForm from(Item item) {
        ItemForm f = new ItemForm();
        f.code = item.getCode();
        f.name = item.getName();
        f.categoryCode = item.getCategoryCode();
        f.unitCode = item.getUnitCode();
        f.standardPrice = item.getStandardPrice();
        f.used = item.isUsed();
        return f;
    }
}
