package com.example.purchase.code.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CommonCodeForm {

    /** 수정할 때는 쓰지 않는다 (코드 값은 바꾸지 않음) */
    @Pattern(regexp = "^[A-Z0-9_-]{1,30}$", message = "코드는 대문자, 숫자, _, - 로 30자까지입니다.")
    private String code;

    @NotBlank(message = "코드 이름을 입력해 주세요.")
    @Size(max = 50, message = "코드 이름은 50자까지입니다.")
    private String name;

    @Min(value = 0, message = "정렬 순서는 0 이상입니다.")
    @Max(value = 9999, message = "정렬 순서는 9999 이하입니다.")
    private int sortOrder;

    private boolean used = true;
}
