package com.example.purchase.approval.service;

import com.example.purchase.common.exception.BusinessException;
import com.example.purchase.common.exception.ErrorCode;
import com.example.purchase.org.domain.Department;
import com.example.purchase.org.domain.Member;
import com.example.purchase.org.domain.Role;
import com.example.purchase.org.repository.MemberRepository;
import com.example.purchase.request.domain.PurchaseRequest;
import com.example.purchase.request.domain.PurchaseRequest.Approver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 결재선 자동 생성 (BR-01). 사람이 결재자를 고르지 않고 조직도에서 찾는다.
 * <ol>
 *   <li>기안 부서가 팀이면 그 팀의 팀장(TEAM_LEADER)</li>
 *   <li>그 팀의 상위 본부의 부서장(DEPT_HEAD). 기안 부서가 본부면 그 본부의 부서장</li>
 *   <li>합계 500만 원 이상이면 구매팀장(PURCHASING 중 직급이 가장 높은 사람)</li>
 * </ol>
 * 기안자 본인이 그 단계의 결재자면 그 단계는 건너뛴다. 결재자가 아예 없으면 상신할 수 없다.
 */
@Component
@RequiredArgsConstructor
public class ApprovalLineResolver {

    public static final BigDecimal PURCHASING_APPROVAL_THRESHOLD = new BigDecimal("5000000");

    private final MemberRepository memberRepository;

    public List<Approver> resolve(PurchaseRequest request) {
        Member requester = request.getRequester();
        Department dept = request.getDepartment();
        List<Approver> approvers = new ArrayList<>();

        Department division = dept.isDivision() ? dept : dept.getParent();
        if (!dept.isDivision()) {
            add(approvers, requester, first(memberRepository.findApprovers(dept.getId(), Role.TEAM_LEADER)),
                    "팀장", dept.getName() + " 팀장");
        }
        add(approvers, requester, first(memberRepository.findApprovers(division.getId(), Role.DEPT_HEAD)),
                "본부장", division.getName() + " 부서장");
        if (request.getTotalAmount().compareTo(PURCHASING_APPROVAL_THRESHOLD) >= 0) {
            add(approvers, requester, first(memberRepository.findByRoleOrderByRank(Role.PURCHASING)),
                    "구매팀장", "구매팀장");
        }
        if (approvers.isEmpty()) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND, "결재할 사람이 없어 상신할 수 없습니다.");
        }
        return approvers;
    }

    private void add(List<Approver> approvers, Member requester, Member approver, String stepName, String who) {
        if (approver == null) {
            throw new BusinessException(ErrorCode.APPROVER_NOT_FOUND,
                    who + "을(를) 찾을 수 없어 상신할 수 없습니다. 관리자에게 조직도를 확인해 달라고 요청해 주세요.");
        }
        if (approver.getId().equals(requester.getId())) {
            return; // 팀장이 직접 올린 요청 → 팀장 단계 건너뜀
        }
        approvers.add(new Approver(approver, stepName));
    }

    private static Member first(List<Member> members) {
        return members.isEmpty() ? null : members.get(0);
    }
}
