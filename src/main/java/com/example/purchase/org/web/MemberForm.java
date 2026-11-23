package com.example.purchase.org.web;

import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberForm {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Pattern(regexp = "^[a-z0-9]{4,20}$", message = "아이디는 영문 소문자와 숫자 4~20자입니다.")
    private String loginId;

    /** 등록 때는 필수, 수정 때는 비워 두면 그대로 (서비스에서 확인) */
    @Size(max = 64, message = "비밀번호는 64자까지입니다.")
    @Pattern(regexp = "^$|^(?=.*[A-Za-z])(?=.*[0-9]).{8,}$", message = "비밀번호는 영문과 숫자를 섞어 8자 이상입니다.")
    private String password;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 30)
    private String name;

    @Email(message = "이메일 형식이 아닙니다.")
    @Size(max = 100)
    private String email;

    @NotNull(message = "부서를 골라 주세요.")
    private Long departmentId;

    @NotNull(message = "직급을 골라 주세요.")
    private Long positionId;

    @NotNull(message = "역할을 골라 주세요.")
    private Role role = Role.EMPLOYEE;

    private boolean enabled = true;

    public static MemberForm from(Member m) {
        MemberForm form = new MemberForm();
        form.loginId = m.getLoginId();
        form.name = m.getName();
        form.email = m.getEmail();
        form.departmentId = m.getDepartment().getId();
        form.positionId = m.getPosition().getId();
        form.role = m.getRole();
        form.enabled = m.isEnabled();
        return form;
    }
}
