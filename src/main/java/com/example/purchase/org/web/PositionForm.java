package com.example.purchase.org.web;

import com.example.purchase.org.domain.Position;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PositionForm {

    @NotBlank(message = "직급 이름을 입력해 주세요.")
    @Size(max = 20, message = "직급 이름은 20자까지입니다.")
    private String name;

    @Min(value = 1, message = "서열은 1 이상입니다.")
    @Max(value = 99, message = "서열은 99 이하입니다.")
    private int rankOrder = 1;

    public static PositionForm from(Position p) {
        PositionForm form = new PositionForm();
        form.name = p.getName();
        form.rankOrder = p.getRankOrder();
        return form;
    }
}
