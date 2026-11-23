package com.example.purchase.code;

import com.example.purchase.code.domain.CodeGroup;
import com.example.purchase.code.service.CodeItem;
import com.example.purchase.code.service.CodeService;
import com.example.purchase.code.web.CodeGroupForm;
import com.example.purchase.code.web.CommonCodeForm;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CodeServiceTest {

    @Autowired CodeService codeService;
    @Autowired EntityManager em;

    @Test
    @DisplayName("서버가 뜰 때 기본 코드(품목 분류, 단위, 구매 목적)가 들어간다")
    void defaultCodes() {
        assertThat(codeService.activeCodes("UNIT")).extracting(CodeItem::code)
                .containsExactly("EA", "BOX", "SET", "KG", "LICENSE");
        assertThat(codeService.activeCodes("ITEM_CATEGORY")).hasSize(5);
    }

    @Test
    @DisplayName("그룹을 만들고 코드를 추가하면 정렬 순서대로 나온다. 사용 중지한 코드는 select 박스에서 빠진다")
    void createAndAdd() {
        codeService.createGroup(groupForm("COLOR", "색상"));
        Long red = codeService.addCode("COLOR", codeForm("RED", "빨강", 20));
        codeService.addCode("COLOR", codeForm("BLUE", "파랑", 10));
        em.flush();
        em.clear();

        assertThat(codeService.activeCodes("COLOR")).extracting(CodeItem::name).containsExactly("파랑", "빨강");

        CommonCodeForm off = codeForm(null, "빨강", 20);
        off.setUsed(false);
        codeService.updateCode("COLOR", red, off);
        em.flush();
        em.clear();

        assertThat(codeService.activeCodes("COLOR")).extracting(CodeItem::name).containsExactly("파랑");
        CodeGroup group = codeService.getWithCodes("COLOR");
        assertThat(group.getCodes()).hasSize(2);
        assertThat(group.getCreatedBy()).isEqualTo("system"); // 로그인 전이면 system
    }

    @Test
    @DisplayName("같은 그룹 코드, 같은 그룹 안의 같은 코드는 만들 수 없다")
    void duplicates() {
        assertThatThrownBy(() -> codeService.createGroup(groupForm("UNIT", "단위2")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_CODE_GROUP);

        assertThatThrownBy(() -> codeService.addCode("UNIT", codeForm("EA", "개2", 99)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_CODE);
    }

    private CodeGroupForm groupForm(String code, String name) {
        CodeGroupForm f = new CodeGroupForm();
        f.setGroupCode(code);
        f.setName(name);
        return f;
    }

    private CommonCodeForm codeForm(String code, String name, int sort) {
        CommonCodeForm f = new CommonCodeForm();
        f.setCode(code);
        f.setName(name);
        f.setSortOrder(sort);
        return f;
    }
}
