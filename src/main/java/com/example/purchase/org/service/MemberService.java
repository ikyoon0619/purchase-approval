package com.example.purchase.org.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.repository.MemberRepository;
import com.example.purchase.org.repository.MemberSearchCond;
import com.example.purchase.org.web.MemberForm;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final DepartmentService departmentService;
    private final PositionService positionService;
    private final PasswordEncoder passwordEncoder;

    public List<Member> search(MemberSearchCond cond) {
        return memberRepository.search(cond);
    }

    public Member get(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Transactional
    public Long create(MemberForm form) {
        if (memberRepository.existsByLoginId(form.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        if (!StringUtils.hasText(form.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "처음 등록할 때는 비밀번호가 필요합니다.");
        }
        Member member = new Member(
                form.getLoginId(),
                passwordEncoder.encode(form.getPassword()),
                form.getName(),
                form.getEmail(),
                departmentService.get(form.getDepartmentId()),
                positionService.get(form.getPositionId()),
                form.getRole());
        return memberRepository.save(member).getId();
    }

    /** 비밀번호 칸을 비워 두면 기존 비밀번호를 그대로 둔다 */
    @Transactional
    public void update(Long id, MemberForm form) {
        Member member = get(id);
        member.update(
                form.getName(),
                form.getEmail(),
                departmentService.get(form.getDepartmentId()),
                positionService.get(form.getPositionId()),
                form.getRole(),
                form.isEnabled());
        if (StringUtils.hasText(form.getPassword())) {
            member.changePassword(passwordEncoder.encode(form.getPassword()));
        }
    }
}
