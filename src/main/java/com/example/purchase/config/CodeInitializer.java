package com.example.purchase.config;

import com.example.purchase.code.domain.CodeGroup;
import com.example.purchase.code.repository.CodeGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 프로그램이 동작하는 데 꼭 필요한 공통코드(품목 분류, 단위)를 넣는다. 모든 환경에서 돈다.
 * 그룹이 이미 있으면 건드리지 않는다 (관리자가 바꾼 값을 덮어쓰지 않도록).
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class CodeInitializer implements ApplicationRunner {

    private final CodeGroupRepository codeGroupRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        add("ITEM_CATEGORY", "품목 분류", new String[][]{
                {"IT", "IT기기"}, {"OFFICE", "사무용품"}, {"CONSUMABLE", "소모품"},
                {"FURNITURE", "가구"}, {"SOFTWARE", "소프트웨어"}});
        add("UNIT", "단위", new String[][]{
                {"EA", "개"}, {"BOX", "박스"}, {"SET", "세트"}, {"KG", "kg"}, {"LICENSE", "라이선스"}});
        add("PURCHASE_PURPOSE", "구매 목적", new String[][]{
                {"WORK", "업무용"}, {"PROJECT", "프로젝트"}, {"REPLACE", "비품 교체"}, {"ETC", "기타"}});
    }

    /** 그룹이 없을 때만 코드와 함께 만든다. 정렬 순서는 10, 20, 30 … (사이에 끼워 넣기 쉽게) */
    void add(String groupCode, String name, String[][] codes) {
        if (codeGroupRepository.existsById(groupCode)) {
            return;
        }
        CodeGroup group = new CodeGroup(groupCode, name);
        for (int i = 0; i < codes.length; i++) {
            group.addCode(codes[i][0], codes[i][1], (i + 1) * 10);
        }
        codeGroupRepository.save(group); // cascade = ALL 이라 코드도 같이 저장된다
        log.info("공통코드 그룹 {}({}개)를 넣었습니다.", groupCode, codes.length);
    }
}
