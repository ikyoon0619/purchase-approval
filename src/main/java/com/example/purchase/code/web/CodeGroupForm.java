package com.example.purchase.code.web;

import com.example.purchase.code.domain.CodeGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CodeGroupForm {

    @NotBlank(message = "그룹 코드를 입력해 주세요.")
    @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$", message = "그룹 코드는 대문자, 숫자, _ 로 2~30자입니다. 예) ITEM_CATEGORY")
    private String groupCode;

    @NotBlank(message = "그룹 이름을 입력해 주세요.")
    @Size(max = 50, message = "그룹 이름은 50자까지입니다.")
    private String name;

    private boolean used = true;

    public static CodeGroupForm from(CodeGroup g) {
        CodeGroupForm form = new CodeGroupForm();
        form.groupCode = g.getGroupCode();
        form.name = g.getName();
        form.used = g.isUsed();
        return form;
    }
}
