package com.example.purchase.request.web;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 요청서 품목 한 줄 입력. 금액(amount)은 받지 않는다: 서버가 계산 (BR-07) */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RequestItemForm {

    @NotNull(message = "품목을 골라 주세요.")
    private Long itemId;

    @NotNull(message = "수량을 입력해 주세요.")
    @Min(value = 1, message = "수량은 1 이상입니다.")
    @Max(value = 100000, message = "수량은 100,000 이하입니다.")
    private Integer quantity;

    @NotNull(message = "단가를 입력해 주세요.")
    @DecimalMin(value = "0", message = "단가는 0원 이상입니다.")
    @Digits(integer = 13, fraction = 0, message = "단가는 원 단위 정수로 입력해 주세요.")
    private BigDecimal unitPrice;
}
