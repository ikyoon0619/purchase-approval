package com.example.purchase.code.service;

import com.example.purchase.code.domain.CodeGroup;
import com.example.purchase.code.repository.CodeGroupRepository;
import com.example.purchase.code.repository.CommonCodeRepository;
import com.example.purchase.code.web.CodeGroupForm;
import com.example.purchase.code.web.CommonCodeForm;
import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CodeService {

    private final CodeGroupRepository codeGroupRepository;
    private final CommonCodeRepository commonCodeRepository;

    /**
     * 그룹 목록 페이지.
     * 그룹 10개를 읽은 뒤 CodeGroupRow.from()에서 각 그룹의 코드를 읽는다.
     * default_batch_fetch_size 덕분에 코드는 "where group_code in (?, ?, ... 10개)" 한 번으로 읽힌다.
     */
    public Page<CodeGroupRow> search(String keyword, Pageable pageable) {
        return codeGroupRepository.search(keyword, pageable).map(CodeGroupRow::from);
    }

    public CodeGroup getWithCodes(String groupCode) {
        return codeGroupRepository.findWithCodes(groupCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.CODE_GROUP_NOT_FOUND));
    }

    /** 다른 화면의 select 박스용 (예: 품목 분류) */
    public List<CodeItem> activeCodes(String groupCode) {
        return commonCodeRepository.findActiveCodes(groupCode).stream().map(CodeItem::from).toList();
    }

    @Transactional
    public String createGroup(CodeGroupForm form) {
        if (codeGroupRepository.existsById(form.getGroupCode())) {
            throw new BusinessException(ErrorCode.DUPLICATE_CODE_GROUP);
        }
        CodeGroup group = new CodeGroup(form.getGroupCode(), form.getName());
        group.update(form.getName(), form.isUsed());
        return codeGroupRepository.save(group).getGroupCode();
    }

    @Transactional
    public void updateGroup(String groupCode, CodeGroupForm form) {
        getWithCodes(groupCode).update(form.getName(), form.isUsed()); // 변경 감지로 update
    }

    @Transactional
    public Long addCode(String groupCode, CommonCodeForm form) {
        CodeGroup group = getWithCodes(groupCode);
        var code = group.addCode(form.getCode(), form.getName(), form.getSortOrder());
        code.update(form.getName(), form.getSortOrder(), form.isUsed());
        commonCodeRepository.save(code); // cascade로도 저장되지만, 새 id를 바로 돌려주려고 직접 저장
        return code.getId();
    }

    @Transactional
    public void updateCode(String groupCode, Long codeId, CommonCodeForm form) {
        getWithCodes(groupCode).findCode(codeId).update(form.getName(), form.getSortOrder(), form.isUsed());
    }
}
