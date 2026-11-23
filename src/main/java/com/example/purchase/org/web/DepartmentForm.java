package com.example.purchase.org.web;

import com.example.purchase.org.domain.Department;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 화면 입력값. 엔티티를 화면에 바로 묶지 않고 폼 객체를 따로 둔다 (검증 규칙과 화면 전용 값 분리) */
@Getter
@Setter
@NoArgsConstructor
public class DepartmentForm {

    @NotBlank(message = "부서 코드를 입력해 주세요.")
    @Pattern(regexp = "^[A-Z][0-9]{3}$", message = "부서 코드는 대문자 1자 + 숫자 3자리입니다. 예) D130")
    private String code;

    @NotBlank(message = "부서명을 입력해 주세요.")
    @Size(max = 50, message = "부서명은 50자까지입니다.")
    private String name;

    /** 비워 두면 본부 */
    private Long parentId;

    private boolean used = true;

    public static DepartmentForm from(Department d) {
        DepartmentForm form = new DepartmentForm();
        form.code = d.getCode();
        form.name = d.getName();
        form.parentId = d.getParent() == null ? null : d.getParent().getId();
        form.used = d.isUsed();
        return form;
    }
}
